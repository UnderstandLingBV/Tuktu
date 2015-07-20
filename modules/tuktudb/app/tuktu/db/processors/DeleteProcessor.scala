package tuktu.db.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import akka.pattern.ask
import scala.concurrent.duration.DurationInt
import akka.util.Timeout

/**
 * Deletes a data packet from the in-memory DB
 */
class DeleteProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var keyFields: List[String] = _
    var sync = false
    
    override def initialize(config: JsObject) {
        keyFields = (config \ "keys").as[List[String]]
        sync = (config \ "sync").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Collect keys for all data items
        val uniqueKeys = (for (datum <- data.data) yield keyFields.map(key => datum(key))).distinct
        
        // Delete for all keys
        if (sync) {
            val futs = for (key <- uniqueKeys) yield {
                // Send request to daemon
                Akka.system.actorSelection("user/tuktu.db.Daemon") ? new DeleteRequest(key, sync)
            }
            
            Future.sequence(futs).map { _ => data }
        }
        else {
            Future {
                for (key <- uniqueKeys) {
                    // Send request to daemon
                    Akka.system.actorSelection("user/tuktu.db.Daemon") ! new DeleteRequest(key, sync)
                }
                
                data
            }
        }
    })
}