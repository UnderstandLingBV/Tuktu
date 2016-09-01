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

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
      val lfuture = for (datum <- data.data) yield {
        val trgt = utils.evaluateTuktuString( target , datum)
        toj match
        {
          case false => getIdentity( trgt ).map{ x => datum + ( resultName -> x ) } 
          case true => getIdentity( trgt ).map{ x => datum + ( resultName ->  oaipmh.xml2jsObject( x ) ) }
        }
      }
      Future.sequence( lfuture ).map{ x => DataPacket( x )  }
    })
    
  /**
   * Utility method to retrieve a repository description
   * @param target: The OAI-PMH identify request
   * @return the repository description
   */
    def getIdentity( target: String ): Future[String] =
    {
        val verb = target + "?verb=Identify"
        oaipmh.fharvest( verb ).map{ response =>
            (response \\ "error").headOption match
            {
                case None => (response \ "Identify").head.toString
                case Some( err ) => response.toString
            }        
        }
    }
}