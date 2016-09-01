package tuktu.processors.json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils.MapToJsObject

/**
 * Converts a field to JSON.
 */
class ConvertFieldToJson(resultName: String) extends BaseProcessor(resultName) {
    /** The field containing the value to be converted to JSON. */
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            datum + (resultName -> MapToJsObject(datum) \ field)
        }
    })
}

/**
 * Converts an entire data packet into JSON
 */
class ConvertToJson(resultName: String) extends BaseProcessor(resultName) {
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            datum + (resultName -> MapToJsObject(datum))
        }
    })
}