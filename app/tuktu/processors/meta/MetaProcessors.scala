package tuktu.processors.meta

import java.lang.reflect.Method
import java.util.concurrent.TimeoutException
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Identify
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import controllers.ProcessorDefinition
import play.api.libs.iteratee.Enumerator

/**
 * Invokes a new generator
 */
class GeneratorConfigProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(1 seconds)
    
    var nextName = ""
    var fieldsToAdd: Option[List[JsObject]] = None
    var genConfig: JsObject = Json.obj()
    
    override def initialize(config: JsObject) = {
        // Get the name of the config file
        nextName = (config \ "name").as[String]
        // See if we need to populate the config file
        fieldsToAdd = (config \ "add_fields").asOpt[List[JsObject]]
        
        // Get config
        genConfig = (config \ "config").as[JsObject]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        // See if we need to add the config or not
        fieldsToAdd match {
            case Some(fields) => {
                // Add fields to our config
                val mapToAdd = (for (field <- fields) yield {
                    // Get source and target name
                    val source = (field \ "source").as[String]
                    val target = (field \ "target").as[String]
                    
                    // Add to our new config, we assume only one element, otherwise this makes little sense
                    target -> data.data.head(source).toString
                }).toMap
                
                // Build the map and turn into JSON config
                val newConfig = genConfig ++ Json.toJson(mapToAdd).asInstanceOf[JsObject]
                
                // Invoke the new generator with custom config
                Akka.system.actorSelection("user/TuktuDispatcher") ! {
                    new controllers.DispatchRequest(nextName, Some(newConfig), false, false, false, None)
                }
            }
            case None => {
                // Invoke the new generator, as-is
                val fut = Akka.system.actorSelection("user/TuktuDispatcher") ! {
                    new controllers.DispatchRequest(nextName, None, false, false, false, None)
                }
            }
        }
        
        // We can still continue with out data
        Future {data}
    })
}

/**
 * This class is used to always have an actor present when data is to be streamed in sync
 */
class SyncStreamForwarder() extends Actor with ActorLogging {
    implicit val timeout = Timeout(5 seconds)
    
    var remoteGenerator: ActorRef = null
    var sync: Boolean = false
    
    def receive() = {
        case setup: (ActorRef, Boolean) => {
            remoteGenerator = setup._1
            sync = setup._2
        }
        case dp: DataPacket => sync match {
            case false => remoteGenerator ! dp
            case true => {
                sender ! Await.result((remoteGenerator ? dp).mapTo[DataPacket], timeout.duration)
            }
        }
        case sp: StopPacket => {
            remoteGenerator ! new StopPacket
            remoteGenerator ! PoisonPill
        }
    }
}

/**
 * Invokes a new generator
 */
class GeneratorStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(5 seconds)
    
    val forwarder = Akka.system.actorOf(Props[SyncStreamForwarder])
    var sync: Boolean = false
    
    override def initialize(config: JsObject) = {
        // Get the name of the config file
        val nextName = (config \ "name").as[String]
        // Node to execute on
        val node = (config \ "node").asOpt[String]
        // Get the processors to send data into
        val next = (config \ "next").as[List[String]]
        // Get the actual config, being a list of processors
        val processors = (config \ "processors").as[List[JsObject]]
        
        sync = (config \ "sync").asOpt[Boolean].getOrElse(false)
        
        // Manipulate config and set up the remote actor
        val customConfig = Json.obj(
            "generators" -> List((Json.obj(
                "name" -> {
                    sync match {
                        case true => "tuktu.generators.SyncStreamGenerator"
                        case false => "tuktu.generators.AsyncStreamGenerator"
                    }
                },
                "result" -> "",
                "config" -> Json.obj(),
                "next" -> next
            ) ++ {
                node match {
                    case Some(n) => Json.obj("node" -> n)
                    case None => Json.obj()
                }
            })),
            "processors" -> processors
        )
        
        // Send a message to our Dispatcher to create the (remote) actor and return us the actorref
        try {
            val fut = Akka.system.actorSelection("user/TuktuDispatcher") ? {
                sync match {
                    case true => new controllers.DispatchRequest(nextName, Some(customConfig), false, true, true, None)
                    case false => new controllers.DispatchRequest(nextName, Some(customConfig), false, true, false, None)
                }
            }
            fut.onSuccess {
                case ar: ActorRef => forwarder ! (ar, sync)
            }
        } catch {
            case e: TimeoutException => {} // skip
            case e: NullPointerException => {}
        }
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] =  Enumeratee.mapM((data: DataPacket) => {
        // Send the result to the generator
        val newData = sync match {
            case true => {
                // Get the result from the generator
                val dataFut = forwarder ? data
                Await.result(dataFut.mapTo[DataPacket], timeout.duration)
            }
            case false => {
                forwarder ! data
                data
            }
        }
        
        // We can still continue with out data
        Future {newData}
    }) compose Enumeratee.onEOF(() => {
        forwarder ! new StopPacket()
        forwarder ! PoisonPill
    })
}

/**
 * Actor that deals with parallel processing
 * 
 */
class ParallelProcessorActor(processor: Enumeratee[DataPacket, DataPacket]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(1 seconds)
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
        
        def runProcessor() = Enumerator(dp) |>> (processor compose sendBackEnum compose controllers.Dispatcher.logEnumeratee) &>> sinkIteratee
    }
    
    def receive() = {
        case dp: DataPacket => {
            // Push to all async processors
            channel.push(dp)

            // Send through our enumeratee
            val p = new senderReturningProcessor(sender, dp)
            Future(p.runProcessor())
        }
    }
}

/**
 * Executes a number of processor-flows in parallel
 */
class ParallelProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(1 seconds)
    
    var actors: List[ActorRef] = null
    var merger: Method = null
    var mergerClass: Any = null
    
    override def initialize(config: JsObject) = {
        // Process config
        val pipelines = (config \ "processors").as[List[JsObject]]
        
        // Set up the merger
        val mergerProcClazz = Class.forName((config \ "merger").as[String])
        mergerClass = mergerProcClazz.getConstructor().newInstance()
        merger = mergerProcClazz.getDeclaredMethods.filter(m => m.getName == "merge").head
        
        // For each pipeline, build the enumeratee
        actors = for (pipeline <- pipelines) yield {
            val start = (pipeline \ "start").as[String]
            val procs = (pipeline \ "pipeline").as[List[JsObject]]
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
                        next
                )
                
                // Return map
                processorId -> procDef
            }).toMap
            
            // Build the processor pipeline for this generator
            val processor = controllers.Dispatcher.buildEnums(List(start), processorMap, "ParalllelProcessor", None).head
            // Set up the actor that will execute this processor
            Akka.system.actorOf(Props(classOf[ParallelProcessorActor], processor))
        }
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        // Send data to actors
        val futs = for (actor <- actors) yield
            actor ? data
        // Get the results
        val results = for (fut <- futs) yield
            Await.result(fut.mapTo[DataPacket], timeout.duration)
            
        // Apply the merger
        Future {merger.invoke(mergerClass, results).asInstanceOf[DataPacket]}
    })
    
}