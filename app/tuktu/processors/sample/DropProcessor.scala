package tuktu.processors.sample

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Drops a number of packets before sending throug
 */
class DropProcessor(resultName: String) extends BaseProcessor(resultName) {
    var amount: Int = _
    override def initialize(config: JsObject) {
        amount = (config \ "amount").as[Int]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.drop(amount)
}