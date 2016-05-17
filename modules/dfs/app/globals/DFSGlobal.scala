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
import java.io.File
import play.api.Play
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import play.api.libs.json.Json
import play.api.libs.json.JsObject
import akka.routing.SmallestMailboxPool
import tuktu.dfs.actors.DFSPersister

class DFSGlobal() extends TuktuGlobal() {
    def loadFileTable() = {
        val nftFile = Cache.getAs[String]("tuktu.dfs.nft_file").getOrElse(Play.current.configuration.getString("tuktu.dfs.nft_file").getOrElse("nft.data"))
        val source = scala.io.Source.fromFile(nftFile)
        val lines = try source.mkString finally source.close()
        // Deserialize
        val content = Json.parse(lines).asInstanceOf[JsObject]
        val files = (content \ "files").as[List[JsObject]]
        val eofs = (content \ "eofs").as[List[JsObject]]
        
        // Iterate over files and eofs to deserialize
        val nft = Cache.getAs[collection.mutable.Map[String, collection.mutable.ArrayBuffer[Int]]]("tuktu.dfs.NodeFileTable")
            .getOrElse(collection.mutable.Map.empty[String, collection.mutable.ArrayBuffer[Int]])
        files.foreach(file => {
            val filename = (file \ "name").as[String]
            val part = (file \ "part").as[Int]
            if (!nft.contains(filename)) nft += filename -> collection.mutable.ArrayBuffer.empty[Int]
            nft(filename) += part
        })
        
        val nftEofs = Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
            .getOrElse(collection.mutable.Map.empty[String, Int])
        eofs.foreach(eof => {
            val filename = (eof \ "name").as[String]
            val part = (eof \ "part").as[Int]
            nftEofs += filename -> part
        })
    }
    
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) = {
        // File table
        Cache.set("tuktu.dfs.NodeFileTable", collection.mutable.Map.empty[String, collection.mutable.ArrayBuffer[Int]])
        Cache.set("tuktu.dfs.NodeFileTable.eofs", collection.mutable.Map.empty[String, Int])
        Cache.set("tuktu.dfs.blocksize", Play.configuration.getInt("tuktu.dfs.blocksize").getOrElse(64))
        Cache.set("tuktu.dfs.prefix", Play.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs"))
        Cache.set("tuktu.dfs.nft_file", Play.configuration.getString("tuktu.dfs.nft_file").getOrElse("nft.data"))
        
        
        // Load file table into memory
        loadFileTable()
        
        // Set up the DFS daemon
        val dfsActor = Akka.system.actorOf(
            SmallestMailboxPool(Play.current.configuration.getInt("tuktu.dfs.daemons").getOrElse(10))
                .props(Props[TDFSDaemon]), name = "tuktu.dfs.Daemon")
        dfsActor ! new InitPacket
        
        // Set up the file persister
        val persistActor =Akka.system.actorOf(
            SmallestMailboxPool(Play.current.configuration.getInt("tuktu.dfs.daemons").getOrElse(10))
                .props(Props[DFSPersister]), name = "tuktu.dfs.Daemon.persist")
        persistActor ! new InitPacket
    }
}