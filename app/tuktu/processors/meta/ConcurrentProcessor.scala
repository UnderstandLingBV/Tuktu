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
import akka.actor.Identify
import akka.actor.ActorIdentity
import scala.concurrent.Promise

case class AddSender()
/**
 * Actor that deals with parallel processing
 */
class ConcurrentProcessorActor(parent: ActorRef, start: String, processorMap: Map[String, ProcessorDefinition], ignoreResults: Boolean, anchorValues: Option[List[String]]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    
    // Build the processor
    val (idString, processor) = {
            val pipeline = controllers.Dispatcher.buildEnums(List(start), processorMap, None, {
                // Add the anchor values to the name
                anchorValues match {
                    case Some(v) => "ConcurrentProcessor[" + v.mkString(",") + "]@" + parent.path.address.toString
                    case None => "ConcurrentProcessor@" + parent.path.address.toString
                }
            }, true)
            (pipeline._1, pipeline._2.head)
    }
    
    // Notify the monitor so we can recover from errors
    Akka.system.actorSelection("user/TuktuMonitor") ! new AppInitPacket(
            idString,
            anchorValues match {
                case Some(v) => "ConcurrentProcessor[" + v.mkString(",") + "]@" + parent.path.address.toString
                case None => "ConcurrentProcessor@" + parent.path.address.toString
            },
            1,
            true,
            Some(self)
    )
    
    val sendBack: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        parent ! data
        data
    })
    // Set up the pipeline
    if (ignoreResults)
        enumerator |>> processor &>> sinkIteratee
    else
        enumerator |>> (processor compose sendBack compose utils.logEnumeratee("")) &>> sinkIteratee

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
    var connectedSenders = 0
    
    // Set up remove actors across the nodes
    val routers = nodes.zipWithIndex.map { ni =>
        val node = ni._1
        val nodeOffset = ni._2
        
        Akka.system.actorOf(RemoteRouterConfig(RoundRobinPool(instanceCount),
            Seq(Address("akka.tcp", "application", node._2.host, node._2.akkaPort))
        ).props(Props(classOf[ConcurrentProcessorActor], self, start, processorMap, ignoreResults, {
            // Anchor values
            if (anchorDomain.size == 0) None else {
                // Find the values that route to this node
                Some((anchorDomain.zipWithIndex.map {value =>
                    (value._2 % nodes.size, value._1)
                } groupBy {_._1})(nodeOffset).map(_._2))
            }
        })))
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
        case as: AddSender => connectedSenders += 1
        case dp: DataPacket => {
            genActor ! dp
            val amount = sentDPs.decrementAndGet
            if (amount == 0 && gotStopPacket.getAndSet(false)) self ! new StopPacket
        }
        case sp: StopPacket => {
            connectedSenders -= 1
            if (connectedSenders == 0) {
                if (sentDPs.get > 0 && !ignoreResults) gotStopPacket.set(true)
                else {
                    routers.foreach(_ ! Broadcast(sp))
                    genActor ! new StopPacket
                    self ! PoisonPill
                }
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
    
    def setUpIntermediateActor(config: JsObject) = {
        // Process config
        val start = (config \ "start").as[String]
        val procs = (config \ "pipeline").as[List[JsObject]]
        val anchorFields = (config \ "anchor_fields").asOpt[List[String]]
        val ignoreResults = (config \ "ignore_results").asOpt[Boolean].getOrElse(false)
        val anchorDomain = (config \ "anchor_domain").asOpt[List[String]].getOrElse(List())
        val concurrentName = (config \ "concurrent_name").as[String]
        
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
        
        // Set up the intermediate actor
        intermediateActor = Akka.system.actorOf(Props(classOf[IntermediateActor], genActor, nodes, instanceCount, start,
                processorMap, anchorFields, anchorDomain, ignoreResults), name = "Concurrent_" + concurrentName)
        // Tell the intermediate actor we are connected
        intermediateActor ! new AddSender
    }

    override def initialize(config: JsObject) {
        val concurrentName = (config \ "concurrent_name").as[String]
        
        // Check if this concurrent processor already exists
        val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
        val homeAddress = Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
        val futures = clusterNodes.map {node =>
            val address = if (node._1 == homeAddress)
                    "/user/Concurrent_" + concurrentName
                else
                    "akka.tcp://application@" + node._1 + ":" + node._2.akkaPort + "/user/Concurrent_" + concurrentName
            // Ask for identity
            (Akka.system.actorSelection(address) ? new Identify(1)).asInstanceOf[Future[ActorIdentity]]
        }
        val promise = Promise[ActorIdentity]()
        futures foreach { _ foreach promise.trySuccess }
        val resultFuture = promise.future
        resultFuture.onSuccess {
            case ai: ActorIdentity => {
                /**
                 * We have found an already existing concurrent actor, use it
                 */
                intermediateActor = ai.getRef
                if (intermediateActor == null)
                    // No concurrent processor was found yet, we create it
                    setUpIntermediateActor(config)
                else
                    // Tell the intermediate actor we are connected
                    intermediateActor ! new AddSender
            }
        }
        resultFuture.onFailure {
            case _ =>
                // No concurrent processor was found yet, we create it
                setUpIntermediateActor(config)
        }
        Await.ready(resultFuture, timeout.duration + (1 seconds))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Forward
        data.data.foreach(datum => intermediateActor ! datum)
        
        data
    }) compose Enumeratee.onEOF(() => {
        intermediateActor ! new StopPacket
    })
}