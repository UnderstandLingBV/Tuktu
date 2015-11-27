package models.modeller

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.concurrent.Akka
import play.api.libs.json._
import play.api.Play.current
import play.api.cache.Cache
import play.api.Logger
import tuktu.api._
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Await

object ModellerEventListener {
    def props(out: ActorRef) = Props(new ModellerEventListener(out))
}

class ModellerEventListener(out: ActorRef) extends Actor {

    var mailbox: Option[ActorRef] = None
    var uuid: Option[String] = None
    val queue = collection.mutable.Queue.empty[Any]

    override def postStop = {
        // Remove event listener
        Akka.system.actorSelection("user/TuktuMonitor") ! new RemoveMonitorEventListener
    }

    def receive = {
        case config: JsObject => {
            // Register event listener
            Akka.system.actorSelection("user/TuktuMonitor") ! new AddMonitorEventListener

            // Dispatch config
            implicit val timeout = Timeout(Cache.getOrElse[Int]("timeout")(5) seconds)
            val future = Akka.system.actorSelection("user/TuktuDispatcher") ? new DispatchRequest("modeller", Some(config), false, true, false, None)
            future.onComplete {
                case Success(ar) => {
                    if (ar.isInstanceOf[ActorRef]) {
                        // Set mailbox and dequeue queue
                        mailbox = Some(ar.asInstanceOf[ActorRef])
                    } else {
                        Logger.error("Modeller Websocket: Unexpected Dispatch Result - Closing WebSocket")
                        self ! PoisonPill
                    }
                }
                case Failure(e) => {
                    Logger.error("Modeller Websocket: Dispatch Request failed - Closing WebSocket", e)
                    self ! PoisonPill
                }
            }
            // Wait until the Dispatch Request is completed
            Await.ready(future, timeout.duration)
        }
        case any => {
            queue.enqueue(any)
            mailbox collect {
                case _ => while(queue.nonEmpty) handle(queue.dequeue)
            }
        }
    }

    def handle: PartialFunction[Any, Unit] = {
        case aip: AppInitPacket => {
            // Store uuid as soon as this mailbox is identified
            mailbox collect {
                case mb => if (mb == aip.mailbox) uuid = Some(aip.uuid)
            }
        }
        case pmp: ProcessorMonitorPacket => {
            // If uuid of packet coincides with flow's uuid, send it to out
            uuid collect {
                case id => if (id == pmp.uuid) {
                    val typeOf = pmp.typeOf match {
                        case BeginType => "BeginType"
                        case EndType   => "EndType"
                        case _         => ""
                    }
                    if (typeOf.nonEmpty) {
                        val obj = Json.obj(
                            "type" -> typeOf,
                            "processor_id" -> pmp.processor_id,
                            "data" -> Json.toJson(pmp.data.data.map(map => tuktu.api.utils.anyMapToJson(map))),
                            "timestamp" -> pmp.timestamp)
                        out ! obj
                    }
                }
            }
        }
        case _ => {}
    }
}