package tuktu.dlib.processors

import akka.util.Timeout

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._

import play.api.libs.ws.WS

import play.api.Play.current

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.io.Codec

import scala.Stream

import tuktu.api._
import tuktu.api.utils.evaluateTuktuString

/**
 * Queries the Europeana API and returns pointers to the resulting records.
 */
class EuropeanaQueryProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var query: String = _
    var apikey: String = _
    var maxresult: Option[Int] = None
    var total: Int = 0
    
    override def initialize(config: JsObject) 
    {
        // Get the Europeana url to query        
        query = (config \ "query").as[String]
        apikey = (config \ "apikey").as[String]
        maxresult = (config \ "maxresult").asOpt[Int]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      new DataPacket(for (datum <- data.data) yield {
                val url =  evaluateTuktuString(query, datum)
                total = 0
                datum + ( resultName -> callEuropeana( url, 0 ) )
            })
    })  
      
    /**
     * Utility method to recursively call the Europeana API until either all the results or the requested number of results is reached
     * @param query: a Europeana query without paging (&start) nor apikey (&wskey) parameters
     * @param start: the first result to collect
     * @return A stream of URLs pointing to the resulting Europeana metadata records 
     */
    def callEuropeana( query: String, start: Int ): Stream[String] =
    {
        val encoding: String = "UTF8"
        val src: Source = Source.fromURL( query + "&start=" + (start + 1) + "&wskey=" + apikey )( Codec.apply( encoding ) )
        val json = Json.parse( src.mkString )
        src.close()
        val itemsCount: Int = (json \ "itemsCount").as[Int]
        val tr: Int = (json \ "totalResults").as[Int]
        val totalResults: Int = maxresult match{
          case None => tr
          case Some(max) => if ((max < tr) && (max > 0)) max else tr
        } 
        val results: Stream[String] = (json \ "items").as[Stream[JsObject]].map{ x => (x \ "link").as[String] }
        total = total + itemsCount
        if (totalResults > total)
        {
            return results ++ callEuropeana( query, total )
        }
        else
        {
            return results
        }       
    }
   
}