package tuktu.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import play.api.libs.concurrent.Execution.Implicits._
import tuktu.api._
import scala.concurrent.Future

class JSProcessor(resultName: String) extends BaseProcessor(resultName) {
    var js: String = _

    override def initialize(config: JsObject) {
        js = (config \ "js").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield datum + (resultName -> js)
    })
}