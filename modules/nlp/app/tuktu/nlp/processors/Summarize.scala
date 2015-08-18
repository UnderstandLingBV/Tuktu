package tuktu.nlp.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import nl.et4it.Tokenizer

class Summarize(resultName: String) extends BaseProcessor(resultName) {
    var textField: String = _
    var tfIdfField: String = _
    var numLines: Int = _
    var asPlainText: Boolean = _
    
    override def initialize(config: JsObject) {
        textField = (config \ "text_field").as[String]
        tfIdfField = (config \ "tfidf_field").as[String]
        numLines = (config \ "num_lines").as[Int]
        asPlainText = (config \ "return_plain_text").asOpt[Boolean].getOrElse(true)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Obtain all the lines
            val lines = {
                val text = datum(textField) match {
                    case t: Seq[String] => t mkString("")
                    case t: Any => t.toString
                }
                
                text.split("[.?!]") toList
            }
            
            // Get the TF-IDF scores
            val tfIdfScores = datum(tfIdfField).asInstanceOf[Map[String, Double]]
            // Go over the lines and compute average TF-IDF score per line, sort by highest
            val sortedLines = (for (line <- lines) yield {
                val tokens = Tokenizer.tokenize(line)
                (line ,tokens.foldLeft(0.0)((a, b) => a + {
                    if (tfIdfScores.contains(b)) tfIdfScores(b) else 0.0
                }))
            }).toList.sortBy(lineScore => lineScore._2).take(numLines).map(lineScore => lineScore._1)
            
            datum + (resultName -> {
                if (asPlainText) sortedLines.mkString(". ") else sortedLines
            })
        })
    })
}