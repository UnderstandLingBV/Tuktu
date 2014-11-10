package tuktu.processors

import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Enumeratee
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsString
import tuktu.api._

class JSProcessor(resultName: String) extends BaseProcessor(resultName) {
	override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
	    new DataPacket(for (datum <- data.data) yield {
	        // Get the JS from the config
		    val js = (config \ "js").as[JsString]
	        datum + (resultName -> js.value)
	    })
    })
}