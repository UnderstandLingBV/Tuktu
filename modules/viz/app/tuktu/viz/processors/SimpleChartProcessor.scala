package tuktu.viz.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json

/**
 * This is a simple chart processor that just takes x and y values. Many of the other charts
 * behave in this way.
 */
abstract class SimpleChartProcessor(resultName: String) extends BaseVizProcessor(resultName) {
    var xField: String = _
    var yField: String = _
    
    override def initialize(config: JsObject) = {
        super.initialize(config)
        xField = (config \ "x_field").as[String]
        yField = (config \ "y_field").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Go over all data and send to the actor
        data.data.foreach(datum =>
            chartActor ! Json.obj(
                    "x" -> datum(xField).asInstanceOf[Double],
                    "y" -> datum(yField).asInstanceOf[Double]
            )
        )
        
        data
    })
}