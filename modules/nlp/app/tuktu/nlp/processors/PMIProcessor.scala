package tuktu.nlp.processors

import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import tuktu.api.utils
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.nlp.models.PMI

class PMIProcessor(resultName: String) extends BaseProcessor(resultName) {
    var docField: String = _
    var labelField: String = _
    var lang: String = _
    var words: List[String] = _
    var retain: Int = _
    
    override def initialize(config: JsObject) {
        docField = (config \ "document_field").as[String]
        labelField = (config \ "label_field").as[String]
        lang = (config \ "language").as[String]
        words = (config \ "words").as[List[String]]
        retain = (config \ "retain").as[Int]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        val language = utils.evaluateTuktuString(lang, data.data.head)
        
        // Get all the documents per class
        val classDocs = data.data.map {datum =>
            (datum(labelField).toString, datum(docField).toString)
        } groupBy { _._1 } map {cd =>
            cd._1 -> cd._2.map(_._2)
        }
        
        // Compute PMI per document
        val pmis = PMI.computePMI(classDocs, words, language, retain)
        
        new DataPacket(List(Map(resultName -> pmis)))
    })
}