package tuktu.ml.processors.timeseries

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.ml.models.timeseries.ChangePointDetection
import org.joda.time.DateTime
import java.util.Date
import tuktu.api.Parsing.ArithmeticParser

/**
 * Detects change points in a series of observations
 */
class ChangePointProcessor(resultName: String) extends BaseProcessor(resultName) {
    var minChange: String = _
    var minRatio: String = _
    var minZScore: String = _
    var inactiveThreshold: String = _
    var windowSize: String = _

    var key: List[String] = _
    var timestampField: String = _
    var valueField: String = _

    override def initialize(config: JsObject) {
        minChange = (config \ "min_change").as[String]
        minRatio = (config \ "min_ratio").as[String]
        minZScore = (config \ "min_z_score").as[String]
        inactiveThreshold = (config \ "inactive_threshold").as[String]
        windowSize = (config \ "window_size").as[String]

        key = (config \ "key").as[List[String]]
        timestampField = (config \ "timestamp_field").as[String]
        valueField = (config \ "value_field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Go over our data and group it on the keys
        val grouped = data.data.groupBy { datum =>
            key.map(k => datum(k))
        }.map { group =>
            (
                group._1,
                {
                    // Group them by timestamp
                    val timeGroups = group._2.groupBy(_(timestampField)).toList
                    // Sort the timestamps too
                    timeGroups.sortWith { (d1, d2) =>
                        (d1._1, d2._1) match {
                            case (a: Long, b: Long) => a < b
                            case (a: Date, b: Date) => a.before(b)
                            case (a: Any, b: Any)   => a.toString < b.toString
                        }
                    }
                })
        }

        // For each timestamp bucket, compute the mean
        val meanGroups = grouped.map { keyedGroup =>
            (keyedGroup._1, {
                keyedGroup._2.map { sortedGroup =>
                    (sortedGroup._1, {
                        val sum = sortedGroup._2.foldLeft(0.0)((a, b) => a + valueToDouble(b(valueField)))
                        val mean = sum / sortedGroup._2.size
                        sortedGroup._2.head + (resultName -> mean)
                    })
                }
            })
        }

        // Now for each, run the peak detection algorithm
        new DataPacket(meanGroups.flatMap { meanGroup =>
            // Get all the timeseries data
            val timeseries = meanGroup._2.map(_._2)
            // Convert to a list of doubles of all means
            val series = timeseries.map(datum => valueToDouble(datum(resultName)))

            // Evaluate parameters
            val newMinChange = ArithmeticParser(minChange, timeseries)
            val newMinRatio = ArithmeticParser(minRatio, timeseries)
            val newMinZScore = ArithmeticParser(minZScore, timeseries)
            val newThreshold = ArithmeticParser(inactiveThreshold, timeseries)
            val newWindowSize = ArithmeticParser(windowSize, timeseries).toInt

            // Run peak detection
            val peaks = {
                // Check if we have enough data points for peak detection to make sense
                if (series.size > newWindowSize)
                    ChangePointDetection(series, newMinChange, newMinRatio,
                        newMinZScore, newThreshold, newWindowSize)
                else List()
            }

            // Return our original timeseries, and add a peak field if required
            for ((ts, idx) <- timeseries.zipWithIndex) yield {
                val peak = peaks.find(p => p.index == idx)
                peak match {
                    case Some(p) => {
                        ts + (resultName -> Map(
                            "size" -> p.size,
                            "type" -> p.cpType.toString))
                    }
                    case None => ts - resultName
                }
            }
        }.toList)
    })

    def valueToDouble(value: Any) = value match {
        case i: Int    => i.toDouble
        case l: Long   => l.toDouble
        case f: Float  => f.toDouble
        case d: Double => d
        case a: Any    => a.toString.toDouble
    }
}