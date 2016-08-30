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
import java.util.concurrent.atomic.AtomicInteger

class ReadGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def _receive = {
        case config: JsValue => {
            var successCounts = new AtomicInteger(0) 
            
            // Get the keys
            val keys = (config \ "keys").as[List[String]]
            val block = (config \ "as_block").asOpt[Boolean].getOrElse(false)
            
            for (key <- keys) {
                // Request value from daemon
                val fut = Akka.system.actorSelection("user/tuktu.db.Daemon") ? new ReadRequest(key)
                
                fut.onSuccess {
                    case rr: ReadResponse => {
                        if (rr.value.nonEmpty) {
                            // Send it in whole or separately?
                            if (block)
                                channel.push(new DataPacket(rr.value))
                            else
                                for (value <- rr.value) channel.push(new DataPacket(List(value)))
                        }
                                
                        // Terminate if we are done
                        if (successCounts.addAndGet(1) == keys.size)
                            self ! new StopPacket
                    }
                    case _ => self ! new StopPacket
                }
                fut.onFailure {
                    case a => self ! new StopPacket
                }
            }
        }
    }
}