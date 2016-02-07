package tuktu.web.processors.analytics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.WebJsEventObject
import tuktu.api.utils

/**
 * Adds even listeners to DOM elements
 */
class EventListenenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var selector: String = _
    var eventName: String = _
    var callback: Option[String] = None

    override def initialize(config: JsObject) {
        selector = (config \ "selector").as[String]
        eventName = (config \ "event_name").as[String]
        callback = (config \ "callback").asOpt[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield datum + (resultName -> new WebJsEventObject(
            utils.evaluateTuktuString(selector, datum),
            utils.evaluateTuktuString(eventName, datum),
            (callback match {
                case Some(cb) => utils.evaluateTuktuString(cb, datum)
                case None => {
                    "function() {tuktuvars." + resultName + "=true};"
                }
            })))
    })
}