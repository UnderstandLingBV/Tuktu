package tuktu.viz.processors

import play.api.libs.json.JsObject
import play.api.libs.json.Json

class TimeLineProcessor(resultName: String) extends BaseVizProcessor(resultName) {
    var timeField: String = _
    var dataField: String = _ 
    
    override def initialize(config: JsObject) = {
        timeField = (config \ "time_field").as[String]
        dataField = (config \ "data_field").as[String]
    }
    
    override def mapToGraphItem(map: Map[String, Any]): JsObject = {
        // Read out the time field and the value field
        val time = map(timeField).asInstanceOf[Long]
        val data = map(dataField).asInstanceOf[Double]
        
        Json.obj(
                "time" -> time, "y" -> data
        )
    }
}