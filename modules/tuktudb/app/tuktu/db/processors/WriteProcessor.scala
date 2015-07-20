package tuktu.db.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api._
import play.api.cache.Cache
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration.DurationInt

/**
 * Writes a data packet to the in-memory DB
 */
class WriteProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var keyFields: List[String] = _
    var sync = false
    
    override def initialize(config: JsObject) {
        keyFields = (config \ "keys").as[List[String]]
        sync = (config \ "sync").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        // Send request to daemon
        if (sync) {
            val fut = Akka.system.actorSelection("user/tuktu.db.Daemon") ? new StoreRequest({
                    for (datum <- data.data) yield new DBObject(keyFields, datum)
            }, sync)
            
            fut.map { _ => data}
        }
        else {
            Future {
                Akka.system.actorSelection("user/tuktu.db.Daemon") ! new StoreRequest({
                        for (datum <- data.data) yield new DBObject(keyFields, datum)
                }, sync)
                
                data
            }
        }
    })
}