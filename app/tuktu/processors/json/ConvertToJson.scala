package tuktu.processors.json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils

/**
 * Converts a field to JSON.
 */
class ConvertToJson(resultName: String) extends BaseProcessor(resultName) {
    /** The field containing the value to be converted to JSON. */
    var field: String = _
    /** Append the data */
    var append: Boolean = false

    override def initialize(config: JsObject) = {
        field = (config \ "field").as[String]
        append = (config \ "append").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {
            new DataPacket(for (datum <- data.data) yield {
                if (!append)
                    datum + (field -> utils.anyMapToJson(datum))
                else
                    datum + (resultName -> utils.anyMapToJson(datum))
            })
        }
    })
}