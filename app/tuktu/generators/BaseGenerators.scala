package tuktu.generators

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee._
import play.api.libs.json.JsValue
import tuktu.api._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Async 'special' generator that just waits for DataPackets to come in and processes them
 */
class AsyncStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends AsyncGenerator(resultName, processors) {
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
class SyncStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(1 seconds)

    var init: Boolean = false
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    val actorRemovingEnumeratee: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
        // First element in the list is the actor ref
        new DataPacket(dp.data.drop(1))
    })
    for (processor <- processors.drop(1))
        processors.foreach(processor => enumerator |>> processor &>> sinkIteratee)
    
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
            if (!init) {
                init = true
                // Make an enumeratee that sends the packets back
                val sendingEnumeratee: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
                    // Get the actor ref and acutal data
                    val actorRef = dp.data.head("ref").asInstanceOf[ActorRef]
                    val newData = new DataPacket(dp.data.drop(1))
                    actorRef ! newData
                    newData
                })
                enumerator |>> (processors.head compose sendingEnumeratee) &>> sinkIteratee
            }
            
            // We add the ActorRef to the datapacket because we need it later on
            channel.push(new DataPacket(Map("ref" -> sender)::dp.data))
        }
    }
}

/**
 * Just generates dummy strings every tick
 */
class DummyGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends AsyncGenerator(resultName, processors) {
    var schedulerActor: Cancellable = null
    var message: String = null
    
    override def receive() = {
        case config: JsValue => {
            // Get the ticking frequency
            val tickInterval = (config \ "interval").as[Int]
            // Get the message to send
            message = (config \ "message").as[String]
            
            // Set up the scheduler
            schedulerActor = Akka.system.scheduler.schedule(
                    0 milliseconds,
                    tickInterval seconds,
                    self,
                    message)
        }
        case sp: StopPacket => {
            schedulerActor.cancel
            cleanup()
        }
        case msg: String => channel.push(new DataPacket(List(Map(resultName -> message))))
    }
}