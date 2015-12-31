package tuktu.dlib.processors.oaipmh

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml._
import tuktu.api._
import tuktu.api.BaseProcessor
import tuktu.dlib.utils.oaipmh

/**
 * Retrieves information about a repository.
 */
class IdentifyProcessor (resultName: String) extends BaseProcessor(resultName) 
{
   var target: String = _
   var toj: Boolean = _
    
    override def initialize(config: JsObject) 
    {
        target = (config \ "target").as[String]
        toj = (config \ "toJSON").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      new DataPacket(for (datum <- data.data) yield {
        val trgt = utils.evaluateTuktuString( target , datum)
        toj match
        {
          case false => datum + ( resultName -> getIdentity( trgt ) )
          case true => datum + ( resultName ->  oaipmh.xml2jsObject( getIdentity( trgt ) ) )
        }        
      })
    })
  /**
   * Utility method to retrieve a repository description
   * @param target: The OAI-PMH identify request
   * @return the repository description
   */
    def getIdentity( target: String ): String =
    {
      val verb = target + "?verb=Identify"
      val response = oaipmh.harvest( verb )
      // check for error
      (response \\ "error").headOption match
      {
        case None => (response \ "Identify").head.toString
        case Some( err ) => response.toString
      }
    }
}