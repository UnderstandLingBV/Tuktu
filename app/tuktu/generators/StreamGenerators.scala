package tuktu.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Identify
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import tuktu.api.CompleteType
import tuktu.api.DataPacket
import tuktu.api.MonitorPacket
import tuktu.api.StopPacket
import tuktu.api.BaseGenerator

/**
 * Async 'special' generator that just waits for DataPackets to come in and processes them
 */
class AsyncStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => { }
        case sp: StopPacket => {
            cleanup()
        }
        case p: DataPacket => channel.push(p)
    }
}

/**
 * Special sync generator that processes a tuple and returns the actual result
 */
class SyncStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(1 seconds)

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
    class senderReturningProcessor(senderActor: ActorRef, dp: DataPacket) {
        // Create enumeratee that will send back
        val sendBackEnum: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
            senderActor ! dp
            dp
        })
        
        def runProcessor() = Enumerator(dp) |>> (processors.head compose sendBackEnum compose logEnumeratee) &>> sinkIteratee
    }
    
    override def receive() = {
        case config: JsValue => {}
        case sp: StopPacket => {
            // Send message to the monitor actor
            val fut = Akka.system.actorSelection("user/TuktuMonitor") ? Identify(None)
            fut.onSuccess {
                case ai: ActorIdentity => {
                    ai.getRef ! new MonitorPacket(
                            CompleteType, self.path.toStringWithoutAddress, "master", 1
                    )
                }
            }
            
            channel.eofAndEnd
            self ! PoisonPill
        }
        case dp: DataPacket => {
            // Push to all async processors
            channel.push(dp)

            // Send through our enumeratee
            val p = new senderReturningProcessor(sender, dp)
            Future(p.runProcessor())
        }
    }
}