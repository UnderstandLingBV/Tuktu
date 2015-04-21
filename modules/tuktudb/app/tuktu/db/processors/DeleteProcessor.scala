package tuktu.db.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import play.api.cache.Cache

class DeleteProcessor(resultName: String) extends BaseProcessor(resultName) {
    var keyField = ""
    var namespace = ""
    
    override def initialize(config: JsObject) = {
        keyField = (config \ "key_field").as[String]
        namespace = (config \ "namespace").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        val fields = data.data.map(datum => datum(keyField))
        // Simply invalidate the cache
        Cache.set(namespace, Cache.getAs[Map[Any, Map[String, Any]]](namespace).getOrElse(Map()).filterNot(elem => fields.contains(elem._1)))
        
        data
    })
}