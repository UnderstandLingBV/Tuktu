package tuktu.processors.json

import tuktu.api.BaseProcessor
import play.api.libs.json._
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json
import tuktu.api.utils

/**
 * Merges multiple JSON objects together in one
 */
class JSONMergerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get the data fields
            val objects = fields.map(field => datum(field).asInstanceOf[JsObject])
            // Merge them one by one
            val json = objects.foldLeft(Json.obj())((a, b) => utils.mergeJson(a, b))

            datum + (resultName -> json)
        }
    })
}