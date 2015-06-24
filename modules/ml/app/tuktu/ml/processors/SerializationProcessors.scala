package tuktu.ml.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global

class SerializeMLModelProcessor(resultName: String) extends BaseProcessor(resultName) {
    var modelName = ""
    var fileName = ""
    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        fileName = (config \ "file_name").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Serialize
        data
    })
}