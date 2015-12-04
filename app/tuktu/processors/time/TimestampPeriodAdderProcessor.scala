package tuktu.processors.time

import java.util.Locale
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils

/**
 * Adds (or substracts) a certain amount of time to a timefield.
 *
 */
class TimestampPeriodAdderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var format: String = _
    var locale: String = _
    var timeField: String = _
    var years: String = _
    var months: String = _
    var weeks: String = _
    var days: String = _
    var hours: String = _
    var minutes: String = _
    var seconds: String = _

    override def initialize(config: JsObject) {
        format = (config \ "format").asOpt[String].getOrElse("")
        locale = (config \ "locale").asOpt[String].getOrElse("US")
        timeField = (config \ "time_field").as[String]
        years = (config \ "years").asOpt[String].getOrElse("0")
        months = (config \ "months").asOpt[String].getOrElse("0")
        weeks = (config \ "weeks").asOpt[String].getOrElse("0")
        days = (config \ "days").asOpt[String].getOrElse("0")
        hours = (config \ "hours").asOpt[String].getOrElse("0")
        minutes = (config \ "minutes").asOpt[String].getOrElse("0")
        seconds = (config \ "seconds").asOpt[String].getOrElse("0")
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            val time = (datum(timeField) match {
                case x: Long     => new DateTime(x)
                case x: DateTime => x
                case x: String   => DateTime.parse(x, DateTimeFormat.forPattern(format).withLocale(Locale.forLanguageTag(locale)))
            })
                .plusYears(utils.evaluateTuktuString(years, datum).toInt)
                .plusMonths(utils.evaluateTuktuString(months, datum).toInt)
                .plusWeeks(utils.evaluateTuktuString(weeks, datum).toInt)
                .plusDays(utils.evaluateTuktuString(days, datum).toInt)
                .plusHours(utils.evaluateTuktuString(hours, datum).toInt)
                .plusMinutes(utils.evaluateTuktuString(minutes, datum).toInt)
                .plusSeconds(utils.evaluateTuktuString(seconds, datum).toInt)

            datum + (resultName -> (datum(timeField) match {
                case _: Long     => time.getMillis
                case _: DateTime => time
                case _: String   => DateTimeFormat.forPattern(format).withLocale(Locale.forLanguageTag(locale)).print(time)
            }))
        }
    })
}