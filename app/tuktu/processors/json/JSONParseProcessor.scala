package tuktu.processors.json

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.utils
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json

/**
 * Turns a field into a valid JSON object
 */
class JSONParseProcessor(resultName: String) extends BaseProcessor(resultName) {
    // The field containing the value to be converted to JSON
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield datum + (resultName -> Json.parse(datum(field).asInstanceOf[String]))
    })
}