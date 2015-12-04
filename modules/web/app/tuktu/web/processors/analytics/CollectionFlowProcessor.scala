package tuktu.web.processors.analytics

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.WebJsObject
import tuktu.api.WebJsNextFlow
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.utils

/**
 * Adds a field to signal what flow to execute to collect the data is captured by all WebJsObjects in this data packet
 */
class CollectionFlowProcessor(resultName: String) extends BaseProcessor(resultName) {
    var flowName: String = _
    
    override def initialize(config: JsObject) {
        flowName = (config \ "flow_name").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            datum + (resultName -> new WebJsNextFlow(
                    utils.evaluateTuktuString(flowName, datum)
            ))
        }
    })
}