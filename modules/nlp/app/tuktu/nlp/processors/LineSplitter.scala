package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Splits a text up in lines (of minimum size)
 */
class LineSplitterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var minSize: Int = _
    var field: String = _
    var separate: Boolean = _
    
    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        minSize = (config \ "min_size").asOpt[Int].getOrElse(1)
        separate = (config \ "separate").asOpt[Boolean].getOrElse(true)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        if (separate) {
            new DataPacket((for (datum <- data.data) yield {
                val text = datum(field).asInstanceOf[String]
                
                // Get all lines of the text
                val lines = text.split("[\\r\\n|\\n]")
                for {
                    line <- lines
                    if (line.size > minSize)
                } yield datum + (resultName -> line)
            }).flatten)
        }
        else {
            new DataPacket(for (datum <- data.data) yield {
                val text = datum(field).asInstanceOf[String]
                
                // Get all lines of the text
                val lines = text.split("[\\r\\n|\\n]")
                datum + (field -> (for {
                    line <- lines
                    if (line.size > minSize)
                } yield line).mkString(" "))
            })
        }
    })
}