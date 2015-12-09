package globals

import akka.actor.Props
import akka.actor.actorRef2Scala
import play.api.Application
import play.api.Play.current
import play.api.libs.concurrent.Akka
import tuktu.api.InitPacket
import tuktu.api.TuktuGlobal
import tuktu.dfs.actors.TDFSDaemon
import play.api.cache.Cache
import akka.actor.ActorRef

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