package tuktu.processors.sample

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Takes a number of elements and then terminates
 */
class TakeProcessor(resultName: String) extends BaseProcessor(resultName) {
    var amount: Int = _
    override def initialize(config: JsObject) {
        amount = (config \ "amount").as[Int]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.take(amount)
}