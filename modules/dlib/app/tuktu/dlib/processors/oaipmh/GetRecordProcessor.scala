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
 * Retrieves an individual metadata record from a repository. Required arguments specify the identifier of the item from which the record is requested and the format of the metadata that should be included in the record.
 */
class GetRecordProcessor(resultName: String) extends BaseProcessor(resultName) 
{
   var target: String = _
   var identifier: String = _
   var metadataPrefix: String = _
   var toj: Boolean = _
    
    override def initialize(config: JsObject) 
    {
        target = (config \ "target").as[String]
        identifier = (config \ "identifier").as[String]
        metadataPrefix = (config \ "metadataPrefix").as[String]
        toj = (config \ "toJSON").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      new DataPacket(for (datum <- data.data) yield {
        val trgt = utils.evaluateTuktuString( target , datum )
        val id = utils.evaluateTuktuString( identifier , datum )
        val prefix = utils.evaluateTuktuString( metadataPrefix , datum )
        val verb = trgt + "?verb=GetRecord&identifier=" + id + "&metadataPrefix=" + prefix
        toj match
        {
          case false => datum + ( resultName -> getRecord( verb ) )
          case true => datum + ( resultName ->  oaipmh.xml2jsObject( getRecord( verb ) ) )
        }        
      })
    })
    
  /**
   * Utility method to retrieve an individual metadata record from a repository
   * @param verb: The OAI-PMH request containing the identifier of the record to get
   * @return the corresponding metadata record
   */
    def getRecord( verb: String ): String =
    {
      val response = oaipmh.harvest( verb )
      // check for error
      (response \\ "error").headOption match
      {
        case None => (response \ "GetRecord" \ "record" \ "@status" ).headOption match
        {
          case Some( status ) => status.text
          case None => (for ( record <- (response \ "GetRecord" \ "record" \ "metadata").head.child; if ( !record.toString.trim.isEmpty ) ) yield{ record.toString }).head
        }
        case Some( err ) => response.toString
      }
    }
}