package tuktu.db.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api._

/**
 * Writes a data packet to the in-memory DB
 */
class WriteProcessor(resultName: String) extends BaseProcessor(resultName) {
    var keyFields: List[String] = _
    
    override def initialize(config: JsObject) {
        keyFields = (config \ "keys").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Send request to daemon
        val fut = Akka.system.actorSelection("user/tuktu.db.Daemon") ! new StoreRequest({
                for (datum <- data.data) yield new DBObject(keyFields, datum)
        })
        
        data
    })
}