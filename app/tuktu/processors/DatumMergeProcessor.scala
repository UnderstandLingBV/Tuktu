package tuktu.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.utils

/**
 * Merges fields of separate datums into one and returns a single datum with the merged fields in it
 */
class DatumMergeProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        DataPacket(List(data.data.foldLeft(Map.empty[String, Any])((a, b) => utils.mergeMap(a, b))))
    })
}