package tuktu.api

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue

case class DataPacket(
        data: List[Map[String, Any]]
) extends java.io.Serializable

case class InitPacket()

case class StopPacket()

case class ResponsePacket(
        json: JsValue
)

case class MonitorPacket(
        typeOf: String,
        payLoad: Any
)

abstract class BaseProcessor(resultName: String) {
    def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = ???
}

abstract class BaseGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends Actor with ActorLogging {
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    
    def receive() = {
        case config: JsValue => ???
        case sp: StopPacket => {
            channel.eofAndEnd
            self ! PoisonPill
        }
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
        case sp: StopPacket => {
            channel.eofAndEnd
            self ! PoisonPill
        }
        case _ => {}
    }
}