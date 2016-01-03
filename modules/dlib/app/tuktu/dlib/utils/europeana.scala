package tuktu.dlib.utils

import play.api.libs.json.JsObject
import play.api.libs.json.Json
//import play.api.libs.ws.WS

import scala.io.Source
import scala.io.Codec

import scala.Stream

/**
 * Some utility functions to automate repetitive Europeana tasks
 */
object europeana 
{
      
    /**
     * Utility method to recursively call the Europeana API until either all the results or the requested number of results is reached
     * @param query: A Europeana query without paging (&start) nor apikey (&wskey) parameters
     * @param start: The first result to collect
     * @param total: The total number of results already collected
     * @param maxresult: The maximum number of results to collect (when None is provided, all the available results are collected)
     * @return A stream of URLs pointing to the resulting Europeana metadata records 
     */
    def callEuropeana( query: String, start: Int, total: Int )(implicit maxresult: Option[Int]): Stream[String] =
    {
        var ttl: Int = total
        val encoding: String = "UTF8"
        val src: Source = Source.fromURL( query + "&start=" + start )( Codec.apply( encoding ) )
        val json = Json.parse( src.mkString )
        src.close()
        val itemsCount: Int = (json \ "itemsCount").as[Int]
        val tr: Int = (json \ "totalResults").as[Int]
        val totalResults: Int = maxresult match{
          case None => tr
          case Some(max) => if ((max < tr) && (max > 0)) max else tr
        } 
        val results: Stream[String] = (json \ "items").as[Stream[JsObject]].map{ x => (x \ "link").as[String] }
        ttl = ttl + itemsCount
        val strt = start + ttl - total
        if (totalResults > ttl)
        {
            return results ++ callEuropeana( query, strt, ttl )
        }
        else
        {
            return results
        }       
    }
  
}