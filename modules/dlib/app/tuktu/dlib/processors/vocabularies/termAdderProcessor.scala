package tuktu.dlib.processors.vocabularies

import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject

import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import tuktu.api._
import tuktu.api.utils.evaluateTuktuString
import tuktu.dlib.actors.Term
import tuktu.dlib.actors.LangTerm

import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration.DurationInt
import akka.actor.Identify
import akka.actor.ActorIdentity

/**
 * Adds a term to a vocabulary in the in-memory vocabulary bank.
 */
class TermAdderProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var source: String = _
    var target: String = _
    var language: Option[String] = _
    var vocabularyName: String = _
    
    override def initialize(config: JsObject) 
    {
        // Get the vocabulary name and content to load      
        source = (config \ "source").as[String]
        target = (config \ "target").as[String]
        language = (config \ "language").as[Option[String]]
        vocabularyName = (config \ "vocabulary").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      for( datum <- data.data)
      {
        val vocabName = evaluateTuktuString(vocabularyName, datum)
        val src = evaluateTuktuString(source, datum)
        val trgt = evaluateTuktuString(target, datum)
        language match{
          case None => Akka.system.actorSelection("user/tuktu.dlib.VocabularyBank") ! new Term( vocabName, src, trgt)
          case Some(lang) => Akka.system.actorSelection("user/tuktu.dlib.VocabularyBank") ! new LangTerm( vocabName, src, lang, trgt)
        }
      }
      data
    })  
}