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
import play.api.Play

/**
 * Starts up the DB Daemon
 */
class DBGlobal() extends TuktuGlobal() {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) = {
        // Get replication factor
        Cache.set("tuktu.db.replication", Play.current.configuration.getInt("tuktu.db.replication").getOrElse(2))
        // Set up the DB daemon
        val dbActor = Akka.system.actorOf(Props[DBDaemon], name = "tuktu.db.Daemon")
        dbActor ! new InitPacket
    }
}