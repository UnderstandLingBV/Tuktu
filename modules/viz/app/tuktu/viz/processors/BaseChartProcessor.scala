package tuktu.viz.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json
import play.api.libs.json.JsValue

/**
 * Base chart processor that simply sends JSON data to a websocket connection so you can
 * plug in your own charting/viz library
 */
class BaseChartProcessor(resultName: String) extends BaseVizProcessor(resultName) {
    var field: String = _
    
    override def initialize(config: JsObject) = {
        super.initialize(config)
        field = (config \ "field").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Go over all data and send to the actor
        data.data.foreach(datum =>
            chartActor ! datum(field).asInstanceOf[JsValue]
        )
        
        data
    })
}