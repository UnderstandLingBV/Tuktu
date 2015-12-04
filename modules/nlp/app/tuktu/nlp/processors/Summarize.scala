package tuktu.nlp.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import nl.et4it.Tokenizer

/**
 * Summarizes text based on TF-IDF scores
 */
class SummarizeProcessor(resultName: String) extends BaseProcessor(resultName) {
    var textField: String = _
    var tfIdfField: String = _
    var numLines: Int = _
    var optimalLength: Int = _
    var asPlainText: Boolean = _
    var base: Double = _
    var preserveOrder: Boolean = _
    
    override def initialize(config: JsObject) {
        textField = (config \ "text_field").as[String]
        tfIdfField = (config \ "tfidf_field").as[String]
        numLines = (config \ "num_lines").as[Int]
        optimalLength = Math.max(1, (config \ "optimal_sentence_length").asOpt[Int].getOrElse(11))
        asPlainText = (config \ "return_plain_text").asOpt[Boolean].getOrElse(true)
        base = Math.max(1.0, (config \ "base").asOpt[Double].getOrElse(1.1))
        preserveOrder = (config \ "preserve_order").asOpt[Boolean].getOrElse(true)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
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
            val sortedLines = (for ((line, index) <- lines.zipWithIndex) yield {
                // Tokenize and filter our (too) short words
                val tokens = Tokenizer.tokenize(line).filter(token => token.size > 2)
                
                ((line, index), {
                    // Sum the TF-IDF scores
                    val score = tokens.foldLeft(0.0)((a, b) => a + {
                        if (tfIdfScores.contains(b)) tfIdfScores(b) else 0.0
                    })
                    // Normalize scores
                    val normalizedScore = score / tokens.size.toDouble
                    // Compensate for length
                    val lenghtNormalizedScore = normalizedScore * Math.pow(base, -Math.abs(tokens.size - optimalLength))
                    
                    // Return the minimum so we can more efficiently do a take later
                    -lenghtNormalizedScore
                })
            }).toList.sortBy(lineScore => lineScore._2).take(numLines)
            
            // Detemine whether or not to preserve order
            val resultLines = ({if (preserveOrder) sortedLines.sortBy(lineScore => lineScore._1._2)
                else sortedLines}).map(lineScore => lineScore._1._1)
            
            datum + (resultName -> {
                if (asPlainText) resultLines.mkString(". ") else resultLines
            })
        }
    })
}