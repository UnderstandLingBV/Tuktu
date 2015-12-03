package tuktu.processors

import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Enumeratee
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsString
import tuktu.api._
import scala.concurrent.Future
import play.api.libs.json.JsObject

class JSProcessor(resultName: String) extends BaseProcessor(resultName) {
    var js = ""

    override def initialize(config: JsObject) {
        js = (config \ "js").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            datum + (resultName -> js)
        })
    })
}