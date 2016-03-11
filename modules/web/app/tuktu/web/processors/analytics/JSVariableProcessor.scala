package tuktu.web.processors.analytics

import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.WebJsCodeObject
import tuktu.api.utils
import tuktu.api.WebJsObject

/**
 * Gets a variable set by JS by some other script
 */
class JSVariableProcessor(resultName: String) extends BaseProcessor(resultName) {
    var code: String = _
    
    override def initialize(config: JsObject) {
        code = (config \ "code").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield datum + (resultName -> new WebJsObject(
                utils.evaluateTuktuString(code, datum), true
        ))
    })
  
}