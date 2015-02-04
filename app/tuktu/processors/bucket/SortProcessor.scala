package tuktu.processors.bucket

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api._
import scala.collection.GenTraversableOnce

/**
 * Sorts elements in a bucket
 */
class SortProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field = ""
    var ascDesc = "asc"
    var dataType = ""
    
    override def initialize(config: JsObject) = {
        // Get the field to sort on
        field = (config \ "field_selector").as[String]
        
        // See if we need ascending or descending
        ascDesc = (config \ "asc_desc").as[String]
        
        // Get the type of the data
        dataType = (config \ "type").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Sort data
        /*data.data.sortWith((a, b) => {
            ascDesc match {
                case "asc" => a(field).asInstanceOf[q] < b(field).asInstanceOf[q]
                case "desc" => {}
            }
        })*/
        data
    })
}