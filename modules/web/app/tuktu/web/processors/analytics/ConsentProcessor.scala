package tuktu.web.processors.analytics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.WebJsCodeObject
import tuktu.api.WebJsSrcObject

class ConsentProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsObject) {
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield (datum + (resultName -> new WebJsSrcObject(
                // Inclusion of consent plugin
                "//cdn.jsdelivr.net/cookie-bar/1/cookiebar-latest.min.js"
        )))
    })
}