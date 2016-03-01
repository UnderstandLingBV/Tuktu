package tuktu.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Replaces a value with a different value, if a specific condition is met
 */
class ConditionalValueReplacer(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsObject) {
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        data
    })
}