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
 * Retrieves the set structure of a repository.
 */
class ListSetsProcessor(resultName: String) extends BaseProcessor(resultName) 
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
        val trgt = utils.evaluateTuktuString( target , datum )
        toj match
        {
          case false => datum + ( resultName -> getSets( trgt, "?verb=ListSets" ) )
          case true => datum + ( resultName ->  getSets( trgt, "?verb=ListSets" ).map{ f => oaipmh.xml2jsObject( f ) }  )
        }        
      })
    })
  
    def getSets( target: String, verb: String ): Seq[String] =
    {
      val response = oaipmh.harvest( target + verb )
      // check for error
      (response \\ "error").headOption match
      {
        case None => {
          val sts = ( response \ "ListSets" \ "set" ).toSeq map { set => set.toString.trim }
          val sets = for ( set <- sts; if ( !set.isEmpty ) ) yield( set )
          val rToken = ( response \ "ListSets" \ "resumptionToken" ).headOption
          rToken match
            {
              case Some( resumptionToken ) => sets ++ getSets( target, ("?verb=ListSets&resumptionToken=" + resumptionToken.text) ) // keep harvesting
              case None => sets // harvesting completed
            }
        }  
        case Some( err ) => Seq(response.toString)
      }
    }
}