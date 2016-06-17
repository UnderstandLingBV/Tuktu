package tuktu.dlib.processors.vocabularies

import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject

import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import tuktu.api._
import tuktu.api.utils.evaluateTuktuString
import tuktu.dlib.actors.JVocabulary
import tuktu.dlib.actors.MVocabulary
import tuktu.dlib.actors.VocabRemovalRequest

import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration.DurationInt
import akka.actor.Identify
import akka.actor.ActorIdentity

/**
 * Loads a vocabulary into the in-memory vocabulary bank.
 */
class VocabularyLoaderProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var vocabulary: String = _
    var vocabularyName: String = _
    
    override def initialize(config: JsObject) 
    {
        // Get the vocabulary name and content to load.    
        vocabulary = (config \ "vocabulary").as[String]
        vocabularyName = (config \ "name").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      for( datum <- data.data)
      {
        val vocabName = evaluateTuktuString(vocabularyName, datum)
        datum(vocabulary) match
        {
          case voc: Map[String, Any] => Akka.system.actorSelection("user/tuktu.dlib.VocabularyBank") ! new MVocabulary( vocabName, voc)
          case voc: JsObject => Akka.system.actorSelection("user/tuktu.dlib.VocabularyBank") ! new JVocabulary( vocabName, voc)
        }
      }
      data
    })  
}

/**
 * Removes a vocabulary from the in-memory vocabulary bank.
 */
class VocabularyRemoverProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var vocabulary: String = _
    
    override def initialize(config: JsObject) 
    {
        // Get the name of the vocabulary to remove      
        vocabulary = (config \ "vocabulary").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      for( datum <- data.data)
      {
        val vocabName = evaluateTuktuString(vocabulary, datum)
        Akka.system.actorSelection("user/tuktu.dlib.VocabularyBank") ! new VocabRemovalRequest( vocabName )
      }
      data
    })  
}