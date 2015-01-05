package tuktu.ml

import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import tuktu.api._
import scala.concurrent.ExecutionContext.Implicits.global

class LogisticRegressionTrainProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Train on the current batch
        
        data
    })
}

class LogisticRegressionApplyProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Classify all items in this batch
        
        data
    })
}