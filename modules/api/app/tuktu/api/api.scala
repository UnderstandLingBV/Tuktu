package tuktu.api

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorLogging
import akka.actor.Identify
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import akka.actor.ActorRef
import play.api.libs.json.JsObject

case class DataPacket(
        data: List[Map[String, Any]]
) extends java.io.Serializable

case class InitPacket()

case class StopPacket()

case class ResponsePacket(
        json: JsValue
)

/**
 * Monitor stuff
 */

sealed abstract class MPType
case object BeginType extends MPType
case object EndType extends MPType
case object CompleteType extends MPType

case class MonitorPacket(
        typeOf: MPType,
        actorName: String,
        branch: String,
        amount: Integer
)

case class MonitorOverviewPacket()

class AppMonitorObject(name: String, startTime: Long) {
    def getName = name
    def getStartTime = startTime
}

case class AppMonitorPacket(
        name: String,
        timestamp: Long,
        status: String
)
/**
 * End monitoring stuff
 */

abstract class BaseProcessor(resultName: String) {
    def initialize(config: JsObject): Unit = {}
    def processor(): Enumeratee[DataPacket, DataPacket] = ???
}

abstract class BufferProcessor(genActor: ActorRef, resultName: String) extends BaseProcessor(resultName: String) {}

abstract class BaseGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(1 seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    
    // Set up pipeline, either one that sends back the result, or one that just sinks
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    senderActor match {
        case Some(ref) => {
            // Set up enumeratee that sends the result back to sender
            val sendBackEnumeratee: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
                ref ! dp
                dp
            })
            processors.foreach(processor => enumerator |>> (processor compose sendBackEnumeratee) &>> sinkIteratee)
        }
        case _ => processors.foreach(processor => enumerator |>> processor &>> sinkIteratee)
    }
    
    def cleanup() = {
        // Send message to the monitor actor
        Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                self.path.toStringWithoutAddress,
                System.currentTimeMillis / 1000L,
                "done"
        )
        
        channel.eofAndEnd
        context.stop(self)
    }
    
    def receive() = {
        case config: JsValue => ???
        case sp: StopPacket => cleanup
        case _ => {}
    }
}



abstract class DataMerger() {
    def merge(packets: List[DataPacket]): DataPacket = ???
}