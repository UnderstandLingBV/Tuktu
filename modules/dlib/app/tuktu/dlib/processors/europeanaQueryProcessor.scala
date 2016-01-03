package tuktu.dlib.processors

import akka.util.Timeout

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject

import play.api.Play.current

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


import tuktu.api._
import tuktu.api.utils.evaluateTuktuString
import tuktu.dlib.utils.europeana

/**
 * Queries the Europeana API and returns pointers to the resulting records.
 */
class EuropeanaQueryProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var query: String = _
    var apikey: String = _
    implicit var maxresult: Option[Int] = None
    
    override def initialize(config: JsObject) 
    {
        // Get the Europeana query and parameters        
        query = (config \ "query").as[String]
        apikey = (config \ "apikey").as[String]
        maxresult = (config \ "maxresult").asOpt[Int]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      new DataPacket(for (datum <- data.data) yield {
                val url =  evaluateTuktuString(query, datum) + "&wskey=" + evaluateTuktuString(apikey, datum)
                maxresult match{
                  case None => datum + ( resultName -> europeana.callEuropeana( url, 1, 0 ) )
                  case Some(mr) => datum + ( resultName -> europeana.callEuropeana( url, 1, 0 ).take(mr) ) 
                }
                
            })
    })  

}