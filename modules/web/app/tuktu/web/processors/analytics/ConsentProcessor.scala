package tuktu.web.processors.analytics

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.utils
import tuktu.api.WebJsEventObject
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.WebJsCodeObject
import tuktu.api.WebJsSrcObject

class ConsentProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsObject) {
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield (datum + ((resultName + "_code") -> new WebJsCodeObject(
            """window.cookieconsent_options = {"message":"This website uses cookies to ensure you get the best experience on our website","dismiss":"Got it!","learnMore":"More info","link":null,"theme":"dark-top"};"""
        )) + ((resultName + "_src") -> new WebJsSrcObject(
                "http://cdnjs.cloudflare.com/ajax/libs/cookieconsent2/1.0.9/cookieconsent.min.js"
        )))
    })
}