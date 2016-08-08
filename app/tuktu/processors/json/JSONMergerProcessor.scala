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
            val json = objects.foldLeft(Json.obj())((a,b) => mergeJson(a, b))
            
            datum + (resultName -> json)
        })
    })
    
    /**
     * Recursive function to merge two JSON objects
     */
    def mergeJson(a: JsObject, b: JsObject): JsObject = {
        // Get all the keys and recurse
        val aSet = a.fieldSet
        val bSet = b.fieldSet
        Json.toJson((for ((aKey, aVal) <- aSet) yield {
            aKey -> {
                if (b.keys.contains(aKey)) {
                    // Get b's element
                    val bVal = b.fieldSet.find(el => el._1 == aKey).getOrElse((aKey, JsNull))._2
                    
                    // We need to merge
                    aVal match {
                        case v: JsBoolean => v
                        case v: JsValue if v == null => {
                            // Check if we can use b's value
                            bVal match {
                                case JsNull => JsNull
                                case w: JsValue => w
                            }
                        }
                        case v: JsNumber => v
                        case v: JsString => v
                        case v: JsObject => {
                            // Recurse
                            bVal match {
                                case w: JsObject => mergeJson(v, w)
                                case w: JsValue => v
                            }
                        }
                        case v: JsArray => v
                    }
                } else aVal
            }
        }).toMap ++ bSet.filter(el => !a.keys.contains(el._1))).as[JsObject]
    }
}