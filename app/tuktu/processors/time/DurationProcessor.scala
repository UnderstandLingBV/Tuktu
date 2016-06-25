package tuktu.processors.time

import org.joda.time.DateTime
import org.joda.time.Days
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.api.utils.evaluateTuktuString

/*
 * Calculate the number of days between two dates expressed in ISO 8601 format (e.g., 2016-06-24) 
 */
class DurationProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var start: String = _
    var end: String = _

    override def initialize(config: JsObject) {
        start = (config \ "start").as[String]
        end = (config \ "end").as[String]
    }
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield 
        {
            val sdate = new DateTime( evaluateTuktuString(start, datum) )
            val edate = new DateTime( evaluateTuktuString(end, datum) )
            datum + (resultName -> Days.daysBetween(sdate, edate).getDays() )
        }
    })
}