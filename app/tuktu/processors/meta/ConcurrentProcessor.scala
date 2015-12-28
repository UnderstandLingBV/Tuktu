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

/**
 * Actor that deals with parallel processing
 */
class ConcurrentProcessorActor(processor: Enumeratee[DataPacket, DataPacket]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore

    /**
     * We must somehow keep track of the sending actor of each data packet. This state is kept within this helper class that
     * is to be instantiated for each data packet
     */
    class senderReturningProcessor(senderActor: ActorRef, dp: DataPacket) {
        // Create enumeratee that will send back
        val sendBackEnum: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
            senderActor ! dp
            dp
        })

        def runProcessor() = Enumerator(dp) |>> (processor compose sendBackEnum compose utils.logEnumeratee("")) &>> sinkIteratee
    }

    def receive() = {
        case sp: StopPacket => {
            self ! PoisonPill
        }
        case dp: DataPacket => {
            // Push to all async processors
            channel.push(dp)

            // Send through our enumeratee
            val p = new senderReturningProcessor(sender, dp)
            p.runProcessor()
        }
    }
}

/**
 * Actor that is always alive and truly async
 */
class IntermediateActor(genActor: ActorRef, node: ClusterNode, instanceCount: Int, processor: Enumeratee[DataPacket, DataPacket]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Set up #instanceCount actors across the nodes to use
    val router = Akka.system.actorOf(RemoteRouterConfig(RoundRobinPool(instanceCount),
            Seq(Address("akka.tcp", "application", node.host, node.akkaPort))
        ).props(Props(classOf[ConcurrentProcessorActor], processor)))
            
    def receive() = { 
        case dp: DataPacket => {
            (router ? dp).map {
                case resultDp: DataPacket => genActor ! resultDp
            }
        }
        case sp: StopPacket => {
            router ! Broadcast(sp)
            genActor ! new StopPacket
        }
    }
}

/**
 * Sets up a sub-flow concurrently and lets datapackets be processed by one of the instances,
 * allowing concurrent processing by multiple instances
 */
class ConcurrentProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    var intermediateActors = List.empty[ActorRef]
    var actorOffset = 0

    override def initialize(config: JsObject) {
        // Process config
        val start = (config \ "start").as[String]
        val procs = (config \ "pipeline").as[List[JsObject]]
        
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

        // Build the processor pipeline for this generator
        val (idString, processor) = {
            val pipeline = controllers.Dispatcher.buildEnums(List(start), processorMap, None, "Concurrent Processor - Unknown")
            (pipeline._1, pipeline._2.head)
        }
        
        // Make all the actors
        intermediateActors = for (node <- nodes) yield
            Akka.system.actorOf(Props(classOf[IntermediateActor], genActor, node._2, instanceCount, processor))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Send data to our actor
        intermediateActors(actorOffset) ! data
        actorOffset = (actorOffset + 1) % intermediateActors.size
        
        data
    }) compose Enumeratee.onEOF(() => {
        intermediateActors.foreach(_ ! new StopPacket)
    })
}