package tuktu.nlp.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Creates N-grams from text
 */
class NgramProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var n: Int = _
    var flatten: Boolean = false
    var chars: Boolean = false
    
    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        n = (config \ "n").as[Int]
        flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
        chars = (config \ "chars").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            datum + (resultName -> {
                datum(field) match {
                    case d: Seq[String] => getNgrams(d)
                    case d: String => if (chars) getNgramsChar(d.toList)
                        else getNgrams(d.split(" "))
                    case d: Any => if (chars) getNgramsChar(d.toString.toList)
                        else getNgrams(d.toString.split(" "))
                }
            })
        })
    })
    
    def getNgramsChar(input: Seq[Char]) = {
        // Get N-grams as seq
        val res = (for (i <- n to input.size - 1) yield {
            input.drop(i - n).take(n)
        }) toList
        
        // Flatten?
        if (flatten)
            res.map(elem => elem.mkString(""))
        else res
    }
    
    def getNgrams(input: Seq[String]) = {
        // Get N-grams as seq
        val res = (for (i <- n to input.size) yield {
            input.drop(i - n).take(n)
        }) toList
        
        // Flatten?
        if (flatten)
            res.map(elem => elem.mkString("")).mkString(" ")
        else res
    }
}