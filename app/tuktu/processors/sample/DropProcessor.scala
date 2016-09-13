package tuktu.processors.sample

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Drops a number of packets before sending throug
 */
class DropProcessor(resultName: String) extends BaseProcessor(resultName) {
    var amount: Int = _
    var datums: Boolean = _
    override def initialize(config: JsObject) {
        amount = (config \ "amount").as[Int]
        datums = (config \ "datums").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = if (datums) Enumeratee.mapM(data => Future {
        new DataPacket(data.data.drop(amount))
    }) else Enumeratee.drop(amount)
}