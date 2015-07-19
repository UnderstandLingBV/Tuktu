package globals

import akka.actor.Props
import tuktu.db.actors.DBDaemon
import play.api.GlobalSettings
import play.api.Application
import play.api.libs.concurrent.Akka
import play.api.Play.current
import tuktu.api.InitPacket
import tuktu.api.TuktuGlobal

/**
 * Starts up the DB Daemon
 */
class DBGlobal() extends TuktuGlobal() {
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) = {
        // Set up the DFS daemon
        val dbActor = Akka.system.actorOf(Props[DBDaemon], name = "tuktu.db.Daemon")
        dbActor ! new InitPacket
    }
}