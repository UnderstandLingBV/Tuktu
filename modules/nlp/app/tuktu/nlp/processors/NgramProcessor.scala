package tuktu.nlp.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.nlp.models.NLP

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
        for (datum <- data) yield {
            datum + (resultName -> {
                val ngs = datum(field) match {
                    case d: Seq[String] => NLP.getNgrams(d, n)
                    case d: String => if (chars) NLP.getNgramsChar(d.toList, n)
                        else NLP.getNgrams(d.split(" "), n)
                    case d: Any => if (chars) NLP.getNgramsChar(d.toString.toList, n)
                        else NLP.getNgrams(d.toString.split(" "), n)
                }
                // Flatten?
                if (flatten) ngs.map(elem => elem.mkString("")).mkString(" ")
                else ngs
            })
        }
    })
}