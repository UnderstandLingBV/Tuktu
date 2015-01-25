package tuktu.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

import akka.actor.ActorRef
import akka.actor.Cancellable
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.StopPacket

/**
 * Just generates dummy strings every tick
 */
class DummyGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var schedulerActor: Cancellable = null
    var message: String = null
    
    override def receive() = {
        case config: JsValue => {
            // Get the ticking frequency
            val tickInterval = (config \ "interval").as[Int]
            // Get the message to send
            message = (config \ "message").as[String]
            
            // Set up the scheduler
            schedulerActor = Akka.system.scheduler.schedule(
                    tickInterval milliseconds,
                    tickInterval milliseconds,
                    self,
                    message)
        }
        case sp: StopPacket => {
            schedulerActor.cancel
            cleanup()
        }
        case msg: String => channel.push(new DataPacket(List(Map(resultName -> message))))
        case x: Any => println("Dummy generator got unexpected packet " + x + "\r\n")
    }
}