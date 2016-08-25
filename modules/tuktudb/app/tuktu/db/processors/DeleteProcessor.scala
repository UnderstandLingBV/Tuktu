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
    
    var key: String = _
    var sync = false
    
    override def initialize(config: JsObject) {
        key = (config \ "key").as[String]
        sync = (config \ "sync").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Collect keys for all data items
        val uniqueKeys = (for (datum <- data.data) yield utils.evaluateTuktuString(key, datum)).distinct
        
        // Delete for all keys
        if (sync) {
            val futs = for (k <- uniqueKeys) yield {
                // Send request to daemon
                Akka.system.actorSelection("user/tuktu.db.Daemon") ? new DeleteRequest(k, sync)
            }
            
            Future.sequence(futs).map { _ => data }
        }
        else Future {
            for (k <- uniqueKeys) {
                // Send request to daemon
                Akka.system.actorSelection("user/tuktu.db.Daemon") ! new DeleteRequest(k, sync)
            }
            
            data
        }
    })
}