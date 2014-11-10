package tuktu.generators

import scala.concurrent.duration.DurationInt
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api.AsyncGenerator
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import tuktu.api.DataPacket
import tuktu.api.AsyncGenerator
import tuktu.api.StopPacket
import tuktu.api.SynchronousGenerator
import play.api.libs.iteratee.Iteratee
import akka.actor.ActorLogging
import play.api.libs.iteratee.Concurrent
import akka.actor.Actor

/**
 * Async 'special' generator that just waits for DataPackets to come in and processes them
 */
class AsyncStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends AsyncGenerator(resultName, processors) {
    override def receive() = {
        case config: JsValue => { }
        case sp: StopPacket => {
            channel.eofAndEnd
            self ! PoisonPill
        }
        case p: DataPacket => channel.push(p)
    }
}

/**
 * Special sync generator that processes a tuple and returns the actual result
 */
class SyncStreamGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]])  extends Actor with ActorLogging {
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    for (processor <- processors.drop(1))
        processors.foreach(processor => enumerator |>> processor &>> sinkIteratee)
    
    override def receive() = {
        case config: JsValue => {}
        case sp: StopPacket => {
            channel.eofAndEnd
            self ! PoisonPill
        }
        case dp: DataPacket => {
            channel.push(dp)
            
            // Make an enumeratee that sends the packets back
            val sendingEnumeratee: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
                sender ! dp
                dp
            })
            enumerator |>> (processors.head compose sendingEnumeratee) &>> sinkIteratee
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
            channel.eofAndEnd
            self ! PoisonPill
        }
        case msg: String => channel.push(new DataPacket(List(Map(resultName -> message))))
    }
}