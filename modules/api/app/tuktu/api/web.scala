package tuktu.api

import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef

class WebJsObject(
        js: String
) extends java.io.Serializable

abstract class TuktuBaseJSGenerator(
        referer: String,
        resultName: String,
        processors: List[Enumeratee[DataPacket, DataPacket]],
        senderActor: Option[ActorRef]
) extends BaseGenerator(resultName, processors, senderActor)