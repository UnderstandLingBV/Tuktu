package globals

import scala.concurrent.duration.DurationInt
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.util.Timeout
import play.api.Application
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import tuktu.api.InitPacket
import tuktu.api.TuktuGlobal
import tuktu.db.actors.DBDaemon
import tuktu.api.DBObject
import tuktu.api.StoreRequest

/**
 * Starts up the DB Daemon
 */
class DBGlobal() extends TuktuGlobal() {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) = {
        // Set up the DFS daemon
        val dbActor = Akka.system.actorOf(Props[DBDaemon], name = "tuktu.db.Daemon")
        dbActor ! new InitPacket
    }
}