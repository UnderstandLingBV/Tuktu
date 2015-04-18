package tuktu.db.generators

import tuktu.api._
import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef

class ReadGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {

}