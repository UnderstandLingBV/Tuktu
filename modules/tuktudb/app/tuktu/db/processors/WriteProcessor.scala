package tuktu.db.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.cache.Cache
import play.api.Play.current

/**
 * Writes a record to the in-memory DB
 */
class WriteProcessor(resultName: String) extends BaseProcessor(resultName) {
    var keyField = ""
    var namespace = ""
    var valueFields: List[String] = _
    
    override def initialize(config: JsObject) {
        keyField = (config \ "key_field").as[String]
        namespace = (config \ "namespace").as[String]
        valueFields = (config \ "value_fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        data.data.foreach(datum => {
            // Get the key fields' values
            val key = datum(keyField)
            // Also get the values
            val values = valueFields.map(valueField => valueField -> datum(valueField)).toMap
            
            // Check if our cache contains this one
            Cache.getAs[Map[Any, Map[String, Any]]](namespace) match {
                case None => Cache.set(namespace, Map(key -> values))
                case Some(cache) => Cache.set(namespace, cache + (key -> values))
            }
        })
        
        data
    })
}