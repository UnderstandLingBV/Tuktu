package tuktu.processors.json

import tuktu.api.BaseProcessor
import play.api.libs.json._
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json

/**
 * Merges multiple JSON objects together in one
 */
class JSONMergerProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Get the data fields
            val objects = fields.map(field => datum(field).asInstanceOf[JsObject])
            // Merge them one by one
            val json = objects.foldLeft(Json.obj())((a, b) => mergeJson(b, a))

            datum + (resultName -> json)
        })
    })

    /**
     * Recursive function to merge two JSON objects
     */
    def mergeJson(a: JsObject, b: JsObject): JsObject = {
        def merge(existingObject: JsObject, otherObject: JsObject): JsObject = {
            val result = existingObject.value ++ otherObject.value.map {
                case (otherKey, otherValue) =>
                    val maybeExistingValue = existingObject.value.get(otherKey)

                    val newValue = (maybeExistingValue, otherValue) match {
                        case (Some(e: JsObject), o: JsObject) => merge(e, o)
                        case _                                => otherValue
                    }
                    otherKey -> newValue
            }
            JsObject(result.toSeq)
        }
        merge(a, b)
    }
}