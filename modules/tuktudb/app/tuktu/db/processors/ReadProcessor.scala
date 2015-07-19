package tuktu.db.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.concurrent.Akka
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.DurationInt

/**
 * Reads a data packet from the Tuktu DB
 */
class ReadProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var keyFields: List[String] = _
    
    override def initialize(config: JsObject) {
        keyFields = (config \ "keys").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Parse keys
        val keys = keyFields.map(key => data.data.head(key))

        // Request value from daemon
        val fut = Akka.system.actorSelection("user/tuktu.db.Daemon") ? new ReadRequest(keys)
        
        fut.map {
            case rr: ReadResponse => new DataPacket(rr.value)
        }
    }) compose Enumeratee.filter((data: DataPacket) => !data.data.isEmpty) // Remove empty data packets
}