package tuktu.processors.meta

import tuktu.api._
import scala.concurrent.Await
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.cache.Cache
import java.lang.reflect.Method
import akka.actor.ActorRef
import scala.concurrent.Future
import akka.util.Timeout
import akka.actor.Props
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import scala.concurrent.duration.DurationInt
import akka.actor.ActorLogging
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import akka.actor.PoisonPill
import akka.actor.Actor
import play.api.libs.iteratee.Concurrent
import akka.pattern.ask
import akka.remote.routing.RemoteRouterConfig
import akka.routing.RoundRobinPool
import akka.actor.Address
import akka.routing.Broadcast
import scala.util.hashing.MurmurHash3
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Actor that deals with parallel processing
 */
class ConcurrentProcessorActor(parent: ActorRef, start: String, processorMap: Map[String, ProcessorDefinition], ignoreResults: Boolean) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    
    // Build the processor
    val (idString, processor) = {
            val pipeline = controllers.Dispatcher.buildEnums(List(start), processorMap, None, "Concurrent Processor - Unknown", true)
            (pipeline._1, pipeline._2.head)
    }
    val sendBack: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        parent ! data
        data
    })
    // Set up the pipeline
    if (ignoreResults)
        enumerator |>> processor &>> sinkIteratee
    else
        enumerator |>> (processor compose sendBack compose utils.logEnumeratee("")) &>> sinkIteratee
    
    // If we don't need to send back, just forward
    if (ignoreResults) enumerator |>> processor &>> sinkIteratee

    def receive() = {
        case sp: StopPacket => self ! PoisonPill
        case dp: DataPacket => channel.push(dp)
    }
}

/**
 * Actor that is always alive and truly async
 */
class IntermediateActor(genActor: ActorRef, nodes: List[(String, ClusterNode)], instanceCount: Int,
        start: String, processorMap: Map[String, ProcessorDefinition], anchorFields: Option[List[String]],
        anchorDomain: List[String], ignoreResults: Boolean) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var actorOffset = 0
    
    // Set up remove actors across the nodes
    val routers = nodes.map { node =>
        Akka.system.actorOf(RemoteRouterConfig(RoundRobinPool(instanceCount),
            Seq(Address("akka.tcp", "application", node._2.host, node._2.akkaPort))
        ).props(Props(classOf[ConcurrentProcessorActor], self, start, processorMap, ignoreResults)))
    }
    
    /**
     * Hashes anchored data to an actor
     */
    def anchorToActorHasher(packet: Map[String, Any], keys: List[String], maxSize: Int) = {
        val keyString = (for (key <- keys) yield packet(key).toString).mkString
        Math.abs(MurmurHash3.stringHash(keyString) % maxSize)
    }
    
    // Keep track of sent DPs
    var sentDPs = new AtomicInteger(0)
    var gotStopPacket = new AtomicBoolean(false)
            
    def receive() = { 
        case datum: Map[String, Any] => {
            if (ignoreResults) {
                // Just forward, we don't care about the response
                anchorFields match {
                    case Some(aFields) => {
                        // Check if we have anchor domain
                        val offset = if (anchorDomain.isEmpty) anchorToActorHasher(datum, aFields, routers.size)
                            else {
                                // See if we can find this anchor in our domain
                                val indexOf = anchorDomain.indexOf(datum(aFields.head))
                                if (indexOf != -1) indexOf % routers.size
                                else anchorToActorHasher(datum, aFields, routers.size)
                            }
                        routers(offset) ! DataPacket(List(datum))
                    }
                    case None => {
                        routers(actorOffset) ! DataPacket(List(datum))
                        actorOffset = (actorOffset + 1) % routers.size
                    }
                }
            } else {
                sentDPs.incrementAndGet
                // Determine where to send it to
                anchorFields match {
                    case Some(aFields) => {
                        val offset = if (anchorDomain.isEmpty) anchorToActorHasher(datum, aFields, routers.size)
                            else {
                                // See if we can find this anchor in our domain
                                val indexOf = anchorDomain.indexOf(datum(aFields.head))
                                if (indexOf != -1) indexOf % routers.size
                                else anchorToActorHasher(datum, aFields, routers.size)
                            }
                        routers(offset) ! DataPacket(List(datum))
                    }
                    case None => {
                        val f = routers(actorOffset) ! DataPacket(List(datum))
                        actorOffset = (actorOffset + 1) % routers.size
                        f
                    }
                }
            }
        }
        case dp: DataPacket => {
            genActor ! dp
            val amount = sentDPs.decrementAndGet
            if (amount == 0 && gotStopPacket.getAndSet(false)) self ! new StopPacket
        }
        case sp: StopPacket => {
            if (sentDPs.get > 0 && !ignoreResults) gotStopPacket.set(true)
            else {
                routers.foreach(_ ! Broadcast(sp))
                genActor ! new StopPacket
                self ! PoisonPill
            }
        }
    }
}

/**
 * Sets up a sub-flow concurrently and lets datapackets be processed by one of the instances,
 * allowing concurrent processing by multiple instances
 */
class ConcurrentProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var intermediateActor: ActorRef = _

    override def initialize(config: JsObject) {
        // Process config
        val start = (config \ "start").as[String]
        val procs = (config \ "pipeline").as[List[JsObject]]
        val anchorFields = (config \ "anchor_fields").asOpt[List[String]]
        val ignoreResults = (config \ "ignore_results").asOpt[Boolean].getOrElse(false)
        val anchorDomain = (config \ "anchor_domain").asOpt[List[String]].getOrElse(List())
        
        // Get the number concurrent instances
        val instanceCount = (config \ "instances").as[Int]
        // Get the nodes to use
        val nodes = {
            val clusterNodes = Cache
                .getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
            val specifiedNodes = (config \ "nodes").asOpt[List[String]].getOrElse(clusterNodes.keys.toList)
            // Get only existing nodes
            for {
                n <- specifiedNodes
                if (clusterNodes.contains(n))
            } yield (n, clusterNodes(n))
        }

        // Define the pipeline
        val processorMap = (for (processor <- procs) yield {
            // Get all fields
            val processorId = (processor \ "id").as[String]
            val processorName = (processor \ "name").as[String]
            val processorConfig = (processor \ "config").as[JsObject]
            val resultName = (processor \ "result").as[String]
            val next = (processor \ "next").as[List[String]]

            // Create processor definition
            val procDef = new ProcessorDefinition(
                processorId,
                processorName,
                processorConfig,
                resultName,
                next)

            // Return map
            processorId -> procDef
        }).toMap
        
        // Make all the actors
        intermediateActor = Akka.system.actorOf(Props(classOf[IntermediateActor], genActor, nodes, instanceCount, start,
                processorMap, anchorFields, anchorDomain, ignoreResults))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Forward
        data.data.foreach(datum => intermediateActor ! datum)
        
        data
    }) compose Enumeratee.onEOF(() => {
        intermediateActor ! new StopPacket
    })
}