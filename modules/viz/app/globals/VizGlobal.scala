package globals

import scala.concurrent.duration.DurationInt

import akka.actor.Props
import akka.util.Timeout
import play.api.Application
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import tuktu.api.InitPacket
import tuktu.api.TuktuGlobal
import tuktu.viz.actor.ChartingActorSupervisor

/**
 * Starts up the DB Daemon
 */
class VizGlobal() extends TuktuGlobal() {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) = {
        // Set up the DFS daemon
        val vizActor = Akka.system.actorOf(Props[ChartingActorSupervisor], name = "tuktu.viz.ChartingActor")
        vizActor ! new InitPacket
    }
}