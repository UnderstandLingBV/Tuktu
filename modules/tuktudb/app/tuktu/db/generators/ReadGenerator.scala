package tuktu.db.generators

import tuktu.api._
import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Akka
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import play.api.cache.Cache
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

class ReadGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get the keys
            val keys = (config \ "keys").as[List[String]]
            val block = (config \ "as_block").asOpt[Boolean].getOrElse(false)
            
            // Request value from daemon
            val fut = Akka.system.actorSelection("user/tuktu.db.Daemon") ? new ReadRequest(keys)
            
            fut.onSuccess {
                case rr: ReadResponse => {
                    // Send it in whole or separately?
                    if (block)
                        channel.push(new DataPacket(rr.value))
                    else
                        for (value <- rr.value) channel.push(new DataPacket(List(value)))
                        
                    // Terminate
                    self ! new StopPacket
                }
                case _ => self ! new StopPacket
            }
            fut.onFailure {
                case a => self ! new StopPacket
            }
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}