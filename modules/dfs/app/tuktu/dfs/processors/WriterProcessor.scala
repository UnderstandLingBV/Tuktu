package tuktu.dfs.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class WriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsObject) {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data
    })
}