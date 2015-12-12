package tuktu.dfs.actors

import akka.actor.ActorLogging
import akka.actor.Actor
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.json.Json

case class PersistRequest()

class DFSPersister extends Actor with ActorLogging {
    def receive() = {
        case pr: PersistRequest => {
            // Persist the NFT to disk
            val nft = Cache.getAs[collection.mutable.Map[String, collection.mutable.ArrayBuffer[Int]]]("tuktu.dfs.NodeFileTable")
                        .getOrElse(collection.mutable.Map.empty[String, collection.mutable.ArrayBuffer[Int]])
            val nftEofs = Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
                        .getOrElse(collection.mutable.Map.empty[String, Int])
            // Create JSON
            val json = Json.obj(
                    "files" -> Json.arr({
                        for (
                                file <- nft;
                                part <- file._2
                        ) yield Json.obj(
                                "name" -> file._1,
                                "part" -> part
                        )
                    }),
                    "eofs" -> Json.arr({
                        for (eof <- nftEofs) yield Json.obj(
                                "name" -> eof._1,
                                "part" -> eof._2
                        )
                    })
            )
        }
        case _ => {}
    }
}