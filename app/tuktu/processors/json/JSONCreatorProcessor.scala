package tuktu.processors.json

import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.utils
import play.api.libs.json.Json

/**
 * Creates a JSON object
 */
class JSONCreatorProcessor(resultName: String) extends BaseProcessor(resultName) {
    var json: String = _
    
    override def initialize(config: JsObject) {
        json = (config \ "json").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            datum + (resultName -> Json.parse(utils.evaluateTuktuString(json, datum)))
        })
    })
}