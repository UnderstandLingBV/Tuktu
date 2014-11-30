package tuktu.nosql.generators

import tuktu.api.AsyncGenerator
import play.api.libs.json.JsValue
import tuktu.api._
import play.api.libs.iteratee.Enumeratee

class KafkaGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends AsyncGenerator(resultName, processors) {
    override def receive() = {
        case config: JsValue => {
            
        }
        case sp: StopPacket => {
            cleanup()
        }
    }
}