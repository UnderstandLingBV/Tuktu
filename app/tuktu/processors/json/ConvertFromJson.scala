package tuktu.processors.json

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsValue
import play.api.libs.json.Json

/**
 * Takes a generic JSON field and turns it into the scala equivalent
 */
class ConvertFromJson(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
                datum + (resultName -> {
                    datum(field) match {
                        case j: JsValue => utils.JsValueToAny(j)
                        case j: String => utils.JsValueToAny(Json.parse(j))
                        case j: Any => utils.JsValueToAny(Json.parse(j.toString))
                    }
                })
        }
    })
}