package tuktu.processors.time

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api._
import scala.concurrent.Future
import java.text.SimpleDateFormat
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Adds a simple timestamp to the data packet
 */
class TimestampAdderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var format: Option[String] = None

    override def initialize(config: JsObject) {
        format = (config \ "format").asOpt[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {
            new DataPacket(for (datum <- data.data) yield {
                format match {
                    case None => datum + (resultName -> System.currentTimeMillis)
                    case Some(frmt) => {
                        val dateFormat = new SimpleDateFormat(frmt)
                        datum + (resultName -> dateFormat.format(System.currentTimeMillis))
                    }
                }
            })
        }
    })
}