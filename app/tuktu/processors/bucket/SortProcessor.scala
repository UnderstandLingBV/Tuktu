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
    
    override def initialize(config: JsObject) = {
        // Get the field to sort on
        field = (config \ "field").as[String]
        
        // See if we need ascending or descending
        ascDesc = (config \ "asc_desc").asOpt[String].getOrElse("asc")
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Sort data
        new DataPacket(data.data.sortWith((m1, m2) => {
            val f1 = m1(field)
            val f2 = m2(field)
            
            (f1, f2) match {
                case (a: String, b: String) => if (ascDesc == "desc") b < a else a < b
                case (a: Char, b: Char) => if (ascDesc == "desc") b < a else a < b
                case (a: Short, b: Short) => if (ascDesc == "desc") b < a else a < b
                case (a: Byte, b: Byte) => if (ascDesc == "desc") b < a else a < b
                case (a: Int, b: Int) => if (ascDesc == "desc") b < a else a < b
                case (a: Integer, b: Integer) => if (ascDesc == "desc") b < a else a < b
                case (a: Double, b: Double) => if (ascDesc == "desc") b < a else a < b
                case (a: Float, b: Float) => if (ascDesc == "desc") b < a else a < b
                case (a: Long, b: Long) => if (ascDesc == "desc") b < a else a < b
                case _ => throw new Exception
            }
        }))
    })
}