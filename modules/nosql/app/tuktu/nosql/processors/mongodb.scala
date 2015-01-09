package tuktu.nosql.processors

import tuktu.api._
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.ExecutionContext.Implicits.global

class MongoDBProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        
        
        data
    })
}