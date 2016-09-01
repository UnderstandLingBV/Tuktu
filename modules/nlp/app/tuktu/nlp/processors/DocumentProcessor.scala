package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Creates a document from a datapacket that contains multiple lines/sentences that need to be merged together
 */
class DocumentProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldName: String = _
    var separator: String = _
    var removeEmptyLines: Boolean = _

    override def initialize(config: JsObject) {
        // Get fields
        fieldName = (config \ "field").as[String]
        separator = (config \ "separator").asOpt[String].getOrElse(" ")
        removeEmptyLines = (config \ "remove_empty_lines").asOpt[Boolean].getOrElse(true)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Get all the text/line data from the packets
        DataPacket(List(Map(resultName -> (for {
            datum <- data.data
            
            str = datum(fieldName) match {
                case s: String => s
                case s: Any => s.toString
            }
            
            if (str.nonEmpty)
        } yield {
            str
        }).mkString(separator))))
    })
}