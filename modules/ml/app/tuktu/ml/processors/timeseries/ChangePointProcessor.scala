package tuktu.ml.processors.timeseries

import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.ml.models.timeseries.ChangePointDetection
import scala.annotation.meta.field
import org.joda.time.DateTime
import java.util.Date
import scala.annotation.meta.field
import scala.annotation.meta.field
import scala.annotation.meta.field

/**
 * Detects change points in a series of observations
 */
class ChangePointProcessor(resultName: String) extends BaseProcessor(resultName) {
    var minChange: Double = _
    var minRatio: Double = _
    var minZScore: Double = _
    var inactiveThreshold: Double = _
    var windowSize: Int = _
    
    var key: List[String] = _
    var timestampField: String = _
    var valueField: String = _
    
    override def initialize(config: JsObject) {
        minChange = (config \ "min_change").as[Double]
        minRatio = (config \ "min_ratio").as[Double]
        minZScore = (config \ "min_z_score").as[Double]
        inactiveThreshold = (config \ "inactive_threshold").as[Double]
        windowSize = (config \ "window_size").as[Int]
        
        key = (config \ "key").as[List[String]]
        timestampField = (config \ "timestamp_field").as[String]
        valueField = (config \ "value_field").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Go over our data and group it on the keys
        val grouped = data.data.groupBy(datum => {
            key.map(k => datum(k))
        }).map(group => {
            (
                    group._1,
                    {
                        // Group them by timestamp
                        val timeGroups = group._2.groupBy(_(timestampField)).toList
                        // Sort the timestamps too
                        timeGroups.sortWith((d1, d2) => (d1._1, d2._1) match {
                            case (a: Long, b: Long) => a < b
                            case (a: Date, b: Date) => a.before(b)
                            case (a: Any, b: Any) => a.toString < b.toString
                        })
                    }
            )
        })
        
        // For each timestamp bucket, compute the mean
        val meanGroups = grouped.map(keyedGroup => {
            (keyedGroup._1, {
                keyedGroup._2.map(sortedGroup => {
                    (sortedGroup._1, {
                        val sum = sortedGroup._2.foldLeft(0.0)((a, b) => a + valueToDouble(b(valueField)))
                        val mean = sum / sortedGroup._2.size
                        sortedGroup._2.head + (resultName -> mean)
                    })
                })
            })
        })
        
        
        // Now for each, run the peak detection algorithm
        new DataPacket(meanGroups.flatMap(meanGroup => {
            // Get all the timeseries data
            val timeseries = meanGroup._2.map(_._2)
            // Convert to a list of doubles of all means
            val series = timeseries.map(datum => valueToDouble(datum(resultName)))
            
            // @TODO: Move parsing to API //Evaluate parameters
            //val parser = new tuktu.utils.TuktuArithmeticsParser(group._2)
            val newMinChange = minChange//parser(minChange)
            val newMinRatio = minRatio//parser(minRatio)
            val newMinZScore = minZScore//parser(minZScore)
            val newThreshold = inactiveThreshold//parser(inactiveThreshold)
            val newWindowSize = windowSize//parser(windowSize)
            
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
                            "type" -> p.cpType.toString
                        ))
                    }
                    case None => ts - resultName
                }
            }
        }).toList)
    })
    
    def valueToDouble(value: Any) = value match {
        case i: Int => i.toDouble
        case l: Long => l.toDouble
        case f: Float => f.toDouble
        case d: Double => d
        case a: Any => a.toString.toDouble
    }
}