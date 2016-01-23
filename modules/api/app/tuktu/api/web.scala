package tuktu.api

import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import akka.actor.ActorLogging
import akka.actor.Actor

abstract class BaseJsObject() extends java.io.Serializable

case class WebJsObject(
        js: Any,
        noQoutes: Boolean = false
) extends BaseJsObject()

case class WebJsNextFlow(
        flowName: String
) extends BaseJsObject()

case class WebJsSrcObject(
        url: String
) extends BaseJsObject()

case class WebJsCodeObject(
        code: String
) extends BaseJsObject()

case class WebJsFunctionObject(
        name: String,
        functionParams: List[String],
        functionBody: String
) extends BaseJsObject()

case class WebJsEventObject(
        elementId: String,
        event: String,
        callback: String
) extends BaseJsObject()

abstract class TuktuBaseJSGenerator(
        referer: String,
        resultName: String,
        processors: List[Enumeratee[DataPacket, DataPacket]],
        senderActor: Option[ActorRef]
) extends Actor with ActorLogging