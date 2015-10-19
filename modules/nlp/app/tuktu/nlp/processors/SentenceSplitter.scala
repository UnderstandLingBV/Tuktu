package tuktu.nlp.processors

import java.text.BreakIterator
import java.util.Locale

import scala.collection.mutable.Buffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Splits a text up in sentences (based on a Locale)
 */
class SentenceSplitterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var locale = Locale.ENGLISH

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        locale = Locale.forLanguageTag((config \ "locale").as[String])
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket((for (datum <- data.data) yield {
            val text = datum(field).asInstanceOf[String]
            val bi = BreakIterator.getSentenceInstance(locale)

            datum + (resultName -> new Splitter(text, bi).toList)
        }))
    })

    /**
     * Wrapper for BreakIterator
     */
    class Splitter(text: String, bi: BreakIterator) extends Iterator[String] {
        bi.setText(text)
        private var start = bi.first
        private var end = bi.next
        def hasNext = end != BreakIterator.DONE
        def next = {
            val result = text.substring(start, end)
            start = end
            end = bi.next
            result
        }
    }
    
}