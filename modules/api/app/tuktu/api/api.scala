package tuktu.api

import akka.actor._
import play.api.libs.iteratee._
import play.api.libs.json.JsValue
import akka.pattern.ask
import scala.concurrent.Await
import java.util.concurrent.TimeoutException
import play.api.Play.current
import play.api.libs.concurrent.Akka
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.util.Timeout

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
    def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = ???
}

abstract class BaseGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(1 seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    
    def cleanup() = {
        // Send message to the monitor actor
        try {
            val fut = Akka.system.actorSelection("user/TuktuMonitor") ? Identify(None)
            val monActor = Await.result(fut.mapTo[ActorIdentity], 2 seconds).getRef
            
            monActor ! new MonitorPacket(
                    CompleteType, self.path.toStringWithoutAddress, "master", 1
            )
        } catch {
            case e: TimeoutException => {} // skip
            case e: NullPointerException => {}
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