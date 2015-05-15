package tuktu.processors.bucket.concurrent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.hashing.MurmurHash3
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.PoisonPill
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import tuktu.api._
import scala.concurrent.Await
import akka.actor.ActorLogging
import akka.actor.Actor
import play.api.cache.Cache
import java.lang.reflect.InvocationTargetException

case class ResultDataPacket(
        packet: DataPacket
)
case class ForwardPacket(
        packet: DataPacket
)

/**
 * Forwards data to a single 
 */
class ConcurrentStreamForwarder(parentActor: ActorRef) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var remoteGenerator: ActorRef = null

    def receive() = {
        case ar: ActorRef => remoteGenerator = ar
        case fp: ForwardPacket => Await.result(remoteGenerator ? fp.packet, timeout.duration)
        case dp: DataPacket => {
            // See where the data packet arrived from
            if (sender != remoteGenerator) {
                // This is the result from our remote execution, we need to pass this on
                parentActor ! new ResultDataPacket(dp)
            }
        }
        case sp: StopPacket => {
            // Last packet, we need to propagate the result
            remoteGenerator ! new StopPacket
        }
    }
}

/**
 * Takes take of distributing computation over several nodes
 */
class ConcurrentHandlerActor(genActor: ActorRef, nodeList: List[String], processorTypes: List[String],
        configs: List[JsValue], resultName: String, mergeHandler: List[List[Map[String, Any]]] => DataPacket) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var nodes = collection.mutable.Map[String, ActorRef]()
    
    // Set up the remote nodes
    for ((node, index) <- nodeList.zipWithIndex) {
        // Construct the processors' config
        val processorsConfig = for ((processor, index) <- processorTypes.zipWithIndex) yield {
            Json.obj(
                    "start" -> ("execute_processor_" + index),
                    "pipeline" -> Json.arr(Json.obj(
                        "id" -> ("execute_processor_" + index),
                        "name" -> processor,
                        "result" -> ("processed_data_" + index),
                        "config" -> (configs(index).asInstanceOf[JsObject] - "nodes"),
                        "next" -> List[String]()
                    ))
            )
        }
        
        val customConfig = Json.obj(
            "generators" -> List(Json.obj(
                "name" -> "tuktu.generators.ConcurrentStreamGenerator",
                "result" -> "",
                "config" -> Json.obj(),
                "next" -> List("bufferer"),
                "nodes" -> Json.obj(
                        "type" -> "SingleNode",
                        "nodes" -> node,
                        "instances" -> 1
                )
            )),
            "processors" -> List(
                Json.obj(
                    "id" -> "bufferer",
                    "name" -> "tuktu.processors.EOFBufferProcessor",
                    "result" -> "buffered_data",
                    "config" -> Json.obj(
                            "sync" -> true
                    ),
                    "next" -> List("execute_processor")),
                Json.obj(
                    "id" -> "execute_processor",
                    "name" -> "tuktu.processors.meta.ParallelProcessor",
                    "result" -> "",
                    "config" -> Json.obj(
                            "merger" -> "tuktu.processors.merge.SerialMerger",
                            "processors" -> processorsConfig
                    ),
                    "next" -> List[String]()
                )
        ))

        // Set up forwarder
        val forwarder = Akka.system.actorOf(Props(classOf[ConcurrentStreamForwarder], self))
        // Submit config to the dispatcher
        val fut = Akka.system.actorSelection("user/TuktuDispatcher") ? new controllers.DispatchRequest(
            "BaseConcurrentProcessor_" + index + "_" + java.util.UUID.randomUUID.toString,
            Some(customConfig),
            false,
            true,
            true,
            Some(forwarder))
        
        // We must sync here
        val ar = Await.result(fut, timeout.duration).asInstanceOf[ActorRef]
        forwarder ! ar
        nodes += node -> forwarder
    }
    
    // Set up distribution class
    val db = new DistributionFunction(configs.head, nodeList)
    val resultTimeout = (configs.head \ "timeout").asOpt[Int].getOrElse(30)
    
    // For gathering the results
    var resultList = collection.mutable.ListBuffer[List[Map[String, Any]]]()

    def receive() = {
        case dp: DataPacket => {
            // Distribute data to one of our nodes
            for (datum <- dp.data)
                nodes(db.nextNode(datum)) ! new ForwardPacket(dp)
        }
        case sp: StopPacket => {
            // This is when we actually need to take action, all data has been streamed
            nodes.map(node => node._2 ! new StopPacket)
        }
        case rdp: ResultDataPacket => {
            // Add result to our list of results
            resultList += rdp.packet.data
            
            // See if we're done
            if (resultList.size == nodes.size) {
                // We must now merge the results, get the 'processed_data' from all of them
                val combinedResult = resultList.toList
                
                // Initialize processors once more
                val resultData = for ((processorType, index) <- processorTypes.zipWithIndex) yield {
                    val procClazz = Class.forName(processorType)
                    val iClazz = procClazz.getConstructor(classOf[String]).newInstance("processed_data_" + index)
                    
                    // Initialize the processor
                    val initMethod = procClazz.getDeclaredMethods.filter(m => m.getName == "initialize").head
                    initMethod.invoke(iClazz, configs(index).asInstanceOf[JsObject] - "nodes")
                
                    // Now invoke the process function
                    val processMethod = procClazz.getDeclaredMethods.filter(m => m.getName == "doProcess").head
                    try {
                        processMethod.invoke(iClazz, combinedResult.map(elem => elem(index))).asInstanceOf[List[Map[String, Any]]]
                    } catch {
                        case e: Exception => {
                            e.printStackTrace()
                            null
                        }
                    }
                }
                
                // Check if we need to merge
                val mergedData = if (mergeHandler == null) new DataPacket(resultData.head)
                    else mergeHandler(resultData)

                // We should send them on to the remote generator now
                genActor ! mergedData
                // Free up
                genActor ! new StopPacket()
            }
        }
    }
}

class DistributionFunction(config: JsValue, nodeList: List[String]) {
    val keys = (config \ "key").asOpt[List[String]]
    var rotationNumber = 0

    private def packetToNodeHasher(packet: Map[String, Any], keys: List[String]) = {
        val keyString = (for (key <- keys) yield packet(key)).mkString
        Math.abs(MurmurHash3.stringHash(keyString) % nodeList.size)
    }

    def nextNode(data: Map[String, Any]) = keys match {
        case Some(ks) => {
            nodeList(packetToNodeHasher(data, ks))
        }
        case None => {
            val res = nodeList(rotationNumber)
            rotationNumber = (rotationNumber + 1) % nodeList.size
            res
        }
    }
}

abstract class BaseConcurrentProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var concurrentHandler: ActorRef = null

    def initializeNodes(nodeList: List[String], processorType: String, config: JsValue, mergeHandler: List[List[Map[String, Any]]] => DataPacket): Unit = {
        this.initializeNodes(nodeList, List(processorType), List(config), mergeHandler)
    }
    
    def initializeNodes(nodeList: List[String], processorTypes: List[String], configs: List[JsValue], mergeHandler: List[List[Map[String, Any]]] => DataPacket): Unit = {
        // Set up concurrent handler
        concurrentHandler = Akka.system.actorOf(Props(classOf[ConcurrentHandlerActor], genActor, nodeList, processorTypes, configs, resultName, mergeHandler))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data.data.foreach(datum => {
            // Offload data packet to our handler
            concurrentHandler ! new DataPacket(List(datum))
        })

        // No need to continue
        new DataPacket(List())
    }) compose Enumeratee.filter((data: DataPacket) => data.data.size > 0) compose Enumeratee.onEOF(() => {
        // Send the end signal to our remote generator
        concurrentHandler ! new StopPacket
    })
}