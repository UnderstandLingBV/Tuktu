package tuktu.processors.json

import play.api.libs.json.{ Json, JsObject, JsValue }
import play.api.libs.iteratee.Enumeratee
import tuktu.api._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Creates a JSON object
 */
class JSONCreatorProcessor(resultName: String) extends BaseProcessor(resultName) {
    var json: JsValue = _

    override def initialize(config: JsObject) {
        json = (config \ "json").as[JsValue]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            datum + (resultName -> utils.evaluateTuktuConfig(json, datum))
        }
    })
}