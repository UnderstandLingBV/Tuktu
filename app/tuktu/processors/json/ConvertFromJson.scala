package tuktu.processors.json

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsValue

/**
 * Takes a generic JSON field and turns it into the scala equivalent
 */
class ConvertFromJson(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var overwrite = false

    override def initialize(config: JsObject) = {
        field = (config \ "field").as[String]
        overwrite = (config \ "overwrite").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            if (overwrite)
                datum + (field -> utils.anyJsonToAny(datum(field).asInstanceOf[JsValue]))
            else
                datum + (resultName -> utils.anyJsonToAny(datum(field).asInstanceOf[JsValue]))
        })
    })
}