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
 * Retrieves the metadata formats available from a repository. An optional argument restricts the request to the formats available for a specific item.
 */
class ListMetadataFormatsProcessor(resultName: String) extends BaseProcessor(resultName) 
{
   var target: String = _
   var identifier: Option[String] = _
   var toj: Boolean = _
    
    override def initialize(config: JsObject) 
    {
        target = (config \ "target").as[String]
        identifier = (config \ "identifier").asOpt[String]
        toj = (config \ "toJSON").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
      val lfuture = for (datum <- data.data) yield {
          val trgt = utils.evaluateTuktuString( target , datum )
          val id = identifier match
          {
              case None => ""
              case Some( i ) => "&identifier=" + utils.evaluateTuktuString( i , datum )
          }
          val verb = trgt + "?verb=ListMetadataFormats" + id
          toj match
          {
              case false => getFormats( verb ).map{ x => datum + ( resultName -> x ) } 
              case true => getFormats( verb ).map{ x => datum + ( resultName ->  x.map{ f => oaipmh.xml2jsObject( f ) } ) } 
          }        
      }
      Future.sequence( lfuture ).map{ x => DataPacket( x )  }
    })
    
  /**
   * Utility method to retrieve the metadata formats supported by a repository
   * @param verb: The OAI-PMH ListFormats request
   * @return the metadata formats supported by the repository
   */
    def getFormats( verb: String ): Future[Seq[String]] =
    {
        oaipmh.fharvest( verb ).map{ response =>
            // check for error
            (response \\ "error").headOption match
            {
                case None => {
                    val f = ((response \ "ListMetadataFormats" \ "metadataFormat" ).toSeq map { frmt => frmt.toString.trim })
                    for (format <- f; if (!format.isEmpty)) yield( format )
                }  
                case Some( err ) => Seq(response.toString)
            }
        }
    }
}