package tuktu.processors.time

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.github.nscala_time.time.Imports._
import org.joda.time.format.DateTimeFormatter
import play.api.libs.json.JsString
import java.util.Locale

/**
 * Floors a given datetimeField, based on the timeframes. Only one timeframe is used, e.g. only years or months,
 * in which a higher timeframe has preference.
 */
class TimestampNormalizerProcessor(resultName: String) extends BaseProcessor(resultName) {

    // the field containing the datetime
    var datetimeField: String = _
    // do we append or overwrite the datetimeField
    var overwrite: Boolean = _

    var millis: Int = _
    var seconds: Int = _
    var minutes: Int = _
    var hours: Int = _
    var days: Int = _
    var months: Int = _
    var years: Int = _

    var dateTimeFormatter: DateTimeFormatter = _

    override def initialize(config: JsObject) {
        val datetimeFormat = (config \ "datetime_format").as[String]
        datetimeField = (config \ "datetime_field").as[String]
        val datetimeLocale = (config \ "datetime_locale").as[String]
        overwrite = (config \ "overwrite").asOpt[Boolean].getOrElse(false)
        millis = (config \ "time" \ "millis").asOpt[Int].getOrElse(0)
        seconds = (config \ "time" \ "seconds").asOpt[Int].getOrElse(0)
        minutes = (config \ "time" \ "minutes").asOpt[Int].getOrElse(0)
        hours = (config \ "time" \ "hours").asOpt[Int].getOrElse(0)
        days = (config \ "time" \ "days").asOpt[Int].getOrElse(0)
        months = (config \ "time" \ "months").asOpt[Int].getOrElse(0)
        years = (config \ "time" \ "years").asOpt[Int].getOrElse(0)

        // make sure at least a timeframe is set
        if (seconds + minutes + hours + days + months + years == 0)
            seconds = 1

        dateTimeFormatter = DateTimeFormat.forPattern(datetimeFormat).withLocale(Locale.forLanguageTag(datetimeLocale))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Make string of it
            val str = datum(datetimeField) match {
                case a: String   => a
                case a: JsString => a.value
                case a: Any      => a.toString
            }

            // Prase
            val dt = dateTimeFormatter.parseDateTime(tuktu.api.utils.evaluateTuktuString(str, datum))
            val newDate = {
                if (years > 0) {
                    val currentYear = dt.year.roundFloorCopy
                    currentYear.minusYears(currentYear.year.get % years)
                } else if (months > 0) {
                    val currentMonth = dt.monthOfYear.roundFloorCopy
                    currentMonth.minusMonths(currentMonth.month.get % months)
                } else if (days > 0) {
                    val currentDay = dt.dayOfYear.roundFloorCopy
                    currentDay.minusDays(currentDay.dayOfYear.get % days)
                } else if (hours > 0) {
                    val currentHours = dt.hourOfDay.roundFloorCopy
                    currentHours.minusHours(currentHours.hourOfDay.get % hours)
                } else if (minutes > 0) {
                    val currentMinutes = dt.minuteOfDay.roundFloorCopy
                    currentMinutes.minusMinutes(currentMinutes.minuteOfDay.get % minutes)
                } else if (seconds > 0) {
                    val currentSeconds = dt.secondOfDay.roundFloorCopy
                    currentSeconds.minusSeconds(currentSeconds.secondOfDay.get % seconds)
                } else {
                    val currentMillis = dt.millisOfDay.roundFloorCopy
                    currentMillis.minusMillis(currentMillis.millisOfDay.get % millis)
                }
            }

            datum + {
                if (overwrite) datetimeField -> newDate
                else resultName -> newDate
            }
        })
    })
}