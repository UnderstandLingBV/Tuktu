package tuktu.processors.time

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import java.util.Date
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.LocalDate

/*
 * Converts a date to miliseconds (unix timestamp)
 */
class DateToMillisProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field = ""

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            datum + (resultName -> {
                datum(field) match {
                    case d: Date      => d.getTime
                    case d: DateTime  => d.getMillis
                    case d: LocalDate => d.toEpochDay * 86400 * 1000
                }
            })
        }
    })
}