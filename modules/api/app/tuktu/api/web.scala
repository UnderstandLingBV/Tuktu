package tuktu.api

import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import akka.actor.ActorLogging
import akka.actor.Actor

case class WebJsObject(
        js: Any
) extends java.io.Serializable

case class WebJsNextFlow(
        flowName: String
) extends java.io.Serializable

abstract class TuktuBaseJSGenerator(
        referer: String,
        resultName: String,
        processors: List[Enumeratee[DataPacket, DataPacket]],
        senderActor: Option[ActorRef]
) extends Actor with ActorLogging