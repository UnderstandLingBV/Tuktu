package tuktu.ml.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

class LogisticRegressionTrainProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsObject) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        // Train on the current batch
        
        Future {data}
    })
}

class LogisticRegressionApplyProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsObject) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        // Classify all items in this batch
        
        Future {data}
    })
}