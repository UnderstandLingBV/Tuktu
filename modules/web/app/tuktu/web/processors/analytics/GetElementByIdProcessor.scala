package tuktu.web.processors.analytics

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.WebJsObject
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.utils
import tuktu.api.BaseJsProcessor

/**
 * Adds JS to fetch a DOM element
 */
class DOMElementFetcherProcessor(resultName: String) extends BaseJsProcessor(resultName) {
    var elementName: String = _
    
    override def initialize(config: JsObject) {
        elementName = (config \ "element_name").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            addJsElement(datum, new WebJsObject(
                    "document.getElementById(\"" + utils.evaluateTuktuString(elementName, datum) + "\")"
            ))
        }
    })
}