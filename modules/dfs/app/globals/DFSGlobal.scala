package globals

import akka.actor.Props
import tuktu.dfs.actors.TDFSDaemon
import play.api.GlobalSettings
import play.api.Application
import play.api.libs.concurrent.Akka
import play.api.Play.current
import tuktu.api.InitPacket
import tuktu.api.TuktuGlobal

class DFSGlobal() extends TuktuGlobal() {
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) = {
        // Set up the DFS daemon
        val dfsActor = Akka.system.actorOf(Props[TDFSDaemon], name = "tuktu.dfs.Daemon")
        dfsActor ! new InitPacket
    }
}