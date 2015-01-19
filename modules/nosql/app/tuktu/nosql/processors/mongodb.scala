package tuktu.nosql.processors

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api._
import scala.concurrent.Future

class MongoDBProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsValue) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {data}
    })
}