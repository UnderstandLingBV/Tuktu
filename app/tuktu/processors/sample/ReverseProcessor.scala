package tuktu.processors.sample

import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Reverses the order of datums inside a DP
 */
class ReverseProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(data.data.reverse)
    })
}