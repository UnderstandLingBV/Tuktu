package tuktu.db.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka

/**
 * Deletes a data packet from the in-memory DB
 */
class DeleteProcessor(resultName: String) extends BaseProcessor(resultName) {
    var keyFields: List[String] = _
    
    override def initialize(config: JsObject) {
        keyFields = (config \ "keys").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Parse keys
        val keys = keyFields.map(key => data.data.head(key))
        
        // Send request to daemon
        val fut = Akka.system.actorSelection("user/tuktu.db.Daemon") ! new DeleteRequest(keys)
        
        data
    })
}