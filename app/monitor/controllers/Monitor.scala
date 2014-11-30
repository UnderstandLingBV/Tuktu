package monitor.controllers

import java.util.concurrent.TimeoutException
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.mvc.Action
import play.api.mvc.Controller
import tuktu.api.MonitorOverviewPacket
import tuktu.api.MPType

object Monitor extends Controller {
    implicit val timeout = Timeout(5 seconds)
    /**
     * Fetches the monitor's info
     */
    def fetchLocalInfo() = Action {
        val monitorMapping = try {
            // Get the monitor
            val fut = Akka.system.actorSelection("user/TuktuMonitor") ? Identify(None)
            val monActor = Await.result(fut.mapTo[ActorIdentity], 2 seconds).getRef
            // Get the mapping
            val mapFut = monActor ? new MonitorOverviewPacket()
            Await.result(mapFut.mapTo[collection.mutable.Map[String, collection.mutable.Map[MPType, collection.mutable.Map[String, Int]]]], 2 seconds)
        } catch {
            case e: TimeoutException => Map[String, Map[MPType, Map[String, Int]]]()
            case e: NullPointerException => Map[String, Map[MPType, Map[String, Int]]]()
        }
        
        Ok(views.html.monitor.showMapping(monitorMapping))
    }
}