package tuktu.db.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.cache.Cache
import play.api.Play.current

class ReadProcessor(resultName: String) extends BaseProcessor(resultName) {
    var keyField = ""
    var namespace = ""
    
    override def initialize(config: JsObject) {
        keyField = (config \ "key_field").as[String]
        namespace = (config \ "namespace").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for {
            datum <- data.data
            
            // Get the key fields' values
            key = datum(keyField)
            
            // Get cached value
            value = Cache.getAs[Map[Any, Map[String, Any]]](namespace)
            if (value != None) 
        } yield {
            value.getOrElse(Map())(namespace)
        })
    }) compose Enumeratee.filter((data: DataPacket) => !data.data.isEmpty) // Remove empty data packets
}