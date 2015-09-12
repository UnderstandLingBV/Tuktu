package tuktu.http.processors.analytics

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.WebJsObject
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Adds JS to fetch a DOM element
 */
class DOMElementFetcherProcessor(resultName: String) extends BaseProcessor(resultName) {
    var elementName: String = _
    
    override def initialize(config: JsObject) {
        elementName = (config \ "element_name").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for (datum <- data.data) yield {
            datum + (resultName -> new WebJsObject(
                    "document.getElementById(\"" + elementName + "\")"
            ))
        })
    })
}