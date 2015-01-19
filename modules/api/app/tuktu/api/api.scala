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

case class DataPacket(
        data: List[Map[String, Any]]
) extends java.io.Serializable

case class InitPacket()

case class StopPacket()

case class ResponsePacket(
        json: JsValue
)

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

abstract class BaseProcessor(resultName: String) {
    def initialize(config: JsValue): Unit = {}
    def processor(): Enumeratee[DataPacket, DataPacket] = ???
}

abstract class BufferProcessor(genActor: ActorRef, resultName: String) extends BaseProcessor(resultName: String) {}

abstract class BaseGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(1 seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    
    def cleanup() = {
        // Send message to the monitor actor
        val fut = Akka.system.actorSelection("user/TuktuMonitor") ? Identify(None)
        fut.onSuccess {
            case ai: ActorIdentity => ai.getRef !new MonitorPacket(CompleteType, self.path.toStringWithoutAddress, "master", 1)
        }
        
        channel.eofAndEnd
        self ! PoisonPill
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

abstract class AsyncGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends BaseGenerator(resultName, processors) {
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    processors.foreach(processor => enumerator |>> processor &>> sinkIteratee)
}

abstract class SynchronousGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends BaseGenerator(resultName, processors) {
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    for (processor <- processors.drop(1))
        processors.foreach(processor => enumerator |>> processor &>> sinkIteratee)
    
    override def receive() = {
        case config: JsValue => sender ! enumerator.through(processors.head)
        case sp: StopPacket => cleanup
        case _ => {}
    }
}