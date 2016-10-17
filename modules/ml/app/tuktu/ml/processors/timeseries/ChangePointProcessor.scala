package tuktu.ml.processors.timeseries

import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.ml.models.timeseries.ChangePointDetection

/**
 * Detects change points in a series of observations
 */
class ChangePointProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var batch: Boolean = _
    var minChange: Double = _
    var minRatio: Double = _
    var minZScore: Double = _
    var inactiveThreshold: Double = _
    var windowSize: Int = _
    
    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        batch = (config \ "batch").asOpt[Boolean].getOrElse(false)
        minChange = (config \ "min_change").as[Double]
        minRatio = (config \ "min_ratio").as[Double]
        minZScore = (config \ "min_z_score").as[Double]
        inactiveThreshold = (config \ "inactive_threshold").as[Double]
        windowSize = (config \ "window_size").as[Int]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // See if we need to get the field from all datums or if the field contains all data
        if (batch) {
            val series = data.data.map(datum => valueToDouble(datum(field)))
            // Run the changepoint detection
            val peaks = ChangePointDetection(series, minChange, minRatio, minZScore, inactiveThreshold, windowSize)
                .map(ChangePointDetection.changePointToMap(_))
            
            new DataPacket(data.data.map(_ + (resultName -> peaks)))
        } else
            new DataPacket(for (datum <- data.data) yield {
                val series = datum(field).asInstanceOf[Seq[Any]].map(valueToDouble(_)).toList
                // Run the changepoint detection
                val peaks = ChangePointDetection(series, minChange, minRatio, minZScore, inactiveThreshold, windowSize)
                    .map(ChangePointDetection.changePointToMap(_))
                
                datum + (resultName -> peaks)
            })
    })
    
    def valueToDouble(value: Any) = value match {
        case i: Int => i.toDouble
        case l: Long => l.toDouble
        case f: Float => f.toDouble
        case d: Double => d
        case a: Any => a.toString.toDouble
    }
}