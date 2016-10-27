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
import akka.routing.SmallestMailboxPool
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Queue
import akka.routing.Broadcast
import tuktu.api.PersistRequest
import scala.concurrent.ExecutionContext.Implicits.global

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
        
        // Create our in-memory DB
        val tuktudb = new TrieMap[String, Queue[Map[String, Any]]]
        // Set up the DB daemon
        val dbActor = Akka.system.actorOf(
            SmallestMailboxPool(Play.current.configuration.getInt("tuktu.db.daemons").getOrElse(10))
                .props(Props(classOf[DBDaemon], tuktudb)), name = "tuktu.db.Daemon")
        dbActor ! Broadcast(new InitPacket)
        
        // Set up persistence if its based on time
        val (persistType, persistValue) = (
            Play.current.configuration.getString("tuktu.db.persiststrategy.type").getOrElse("time"),
            Play.current.configuration.getInt("tuktu.db.persiststrategy.value").getOrElse(1)
        )
        // Persist only when a time limit has collapsed
        if (persistType == "time") {
            Akka.system.scheduler.schedule(
                    persistValue seconds,
                    persistValue seconds,
                    dbActor,
                    new PersistRequest
            )
        }
    }
}