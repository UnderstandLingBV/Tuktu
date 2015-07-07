package globals

import akka.actor.Props
import tuktu.dfs.actors.DFSDaemon
import play.api.GlobalSettings
import play.api.Application
import play.api.libs.concurrent.Akka
import play.api.Play.current
import tuktu.api.InitPacket

class DFSGlobal extends GlobalSettings {
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) {
        // Set up the DFS daemon
        val dfsActor = Akka.system.actorOf(Props[DFSDaemon], name = "tuktu.dfs.Daemon")
        dfsActor ! new InitPacket
    }
}