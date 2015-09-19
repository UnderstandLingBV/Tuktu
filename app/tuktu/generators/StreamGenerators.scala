package tuktu.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import tuktu.api._

/**
 * Async 'special' generator that just waits for DataPackets to come in and processes them
 */
class AsyncStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case ip: InitPacket => setup
        case config: JsValue => { }
        case sp: StopPacket => cleanup
        case p: DataPacket => channel.push(p)
    }
}

/**
 * Special sync generator that processes a tuple and returns the actual result
 */
class SyncStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends Actor with ActorLogging {
    implicit var timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    var dontReturnAtAll = false
    
    // Every processor but the first gets treated as asynchronous
    for (processor <- processors.drop(1))
        processors.foreach(processor => enumerator |>> processor &>> sinkIteratee)
        
    /**
     * We must somehow keep track of the sending actor of each data packet. This state is kept within this helper class that
     * is to be instantiated for each data packet
     */
    class senderReturningProcessor(sActor: ActorRef, dp: DataPacket) {
        // Create enumeratee that will send back
        val sendBackEnum: Enumeratee[DataPacket, DataPacket] = Enumeratee.map((d: DataPacket) => {
            if (!dontReturnAtAll) {
                val sourceActor = {
                    senderActor match {
                        case Some(a) => a
                        case None => sActor
                    }
                }
                
                sourceActor ! d
            }
            
            d
        })
        
        def runProcessor() = {
            Enumerator(dp) |>> (processors.head compose sendBackEnum) &>> sinkIteratee
        }
    }

    def receive() = {
        case ip: InitPacket => { }
        case config: JsValue => {
            // Return or not?
            dontReturnAtAll = (config \ "no_return").asOpt[Boolean].getOrElse(false)
            // Custom timeout?
            (config \ "timeout").asOpt[Int] match {
                case Some(t) => {
                    // Overwrite timeout
                    timeout = Timeout(t seconds)
                }
                case None => {}
            }
        }
        case sp: StopPacket => {
            // Send message to the monitor actor
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                    self,
                    "done"
            )
            
            val enum: Enumerator[DataPacket] = Enumerator.enumInput(Input.EOF)
            enum |>> processors.head &>> sinkIteratee

            channel.eofAndEnd           
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
 * Special case of stream generator that makes sure data is ordered properly, used for concurrent aggregating
 */
class ConcurrentStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    var dontReturnAtAll = false
    
    // Logging enumeratee
    def logEnumeratee[T] = Enumeratee.recover[T] {
        case (e, input) => System.err.println("Synced generator error happened on: " + input, e)
    }
    
    // Every processor but the first gets treated as asynchronous
    for (processor <- processors.drop(1))
        processors.foreach(processor => enumerator |>> (processor compose logEnumeratee) &>> sinkIteratee)
        
    /**
     * We must somehow keep track of the sending actor of each data packet. This state is kept within this helper class that
     * is to be instantiated for each data packet
     */
    class senderReturningProcessor(sActor: ActorRef, dp: DataPacket) {
        // Create enumeratee that will send back
        val sendBackEnum: Enumeratee[DataPacket, DataPacket] = Enumeratee.map((d: DataPacket) => {
            sActor ! "ok"
            
            d
        })
        
        def runProcessor() = {
            Enumerator(dp) |>> (processors.head compose sendBackEnum compose logEnumeratee) &>> sinkIteratee
        }
    }
    
    def receive() = {
        case ip: InitPacket => {
            // Send the monitoring actor notification of start
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                    self,
                    "start"
            )
        }
        case config: JsValue => dontReturnAtAll = (config \ "no_return").asOpt[Boolean].getOrElse(false)
        case sp: StopPacket => {
            // Send message to the monitor actor
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                    self,
                    "done"
            )
            
            val enum: Enumerator[DataPacket] = Enumerator.enumInput(Input.EOF)
            enum |>> (processors.head compose logEnumeratee) &>> sinkIteratee

            channel.eofAndEnd           
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
 * Special sync generator that processes a tuple and returns the actual result
 */
class EOFSyncStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    
    // Logging enumeratee
    def logEnumeratee[T] = Enumeratee.recover[T] {
        case (e, input) => System.err.println("Synced generator error happened on: " + input, e)
    }
    
    // Every processor but the first gets treated as asynchronous
    for (processor <- processors.drop(1))
        processors.foreach(processor => enumerator |>> (processor compose logEnumeratee) &>> sinkIteratee)
        
    /**
     * We must somehow keep track of the sending actor of each data packet. This state is kept within this helper class that
     * is to be instantiated for each data packet
     */
    class senderReturningProcessor(sActor: ActorRef, sendBack: Boolean) {
        // Create enumeratee that will send back
        val sendBackEnum: Enumeratee[DataPacket, DataPacket] = Enumeratee.map((d: DataPacket) => {
            if (sendBack) {
                val sourceActor = {
                    senderActor match {
                        case Some(a) => a
                        case None => sActor
                    }
                }
                
                sourceActor ! d
            }
            
            d
        })
        
        def runProcessor(enum: Enumerator[DataPacket]) = {
            enum |>> (processors.head compose sendBackEnum compose logEnumeratee) &>> sinkIteratee
        }
    }
    
    def receive() = {
        case ip: InitPacket => {
            // Send the monitoring actor notification of start
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                    self,
                    "start"
            )
        }
        case config: JsValue => {}
        case sp: StopPacket => {
            // Send message to the monitor actor
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                    self,
                    "done"
            )
            
            channel.eofAndEnd
            self ! PoisonPill
        }
        case dp: DataPacket => {
            // Push to all async processors
            channel.push(dp)
            
            // Send through our enumeratee
            val p = new senderReturningProcessor(sender, true)
            p.runProcessor(Enumerator(dp))
            p.runProcessor(Enumerator.enumInput(Input.EOF))
        }
    }
}