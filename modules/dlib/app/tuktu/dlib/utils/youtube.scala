package tuktu.dlib.utils

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object youtube 
{
    /**
     * Utility method to recursively call the Youtube data API until all the results available are obtained
     * @param query: A complete youtube data api query 
     */
    def callYoutube( query: String ): Future[List[JsObject]] =
    {
        val future = WS.url( query ).get
        future.flatMap( response => {
            val res = response.json
            val results = ( res \ "items" ).as[List[JsObject]]
            ( res \ "nextPageToken" ).asOpt[String] match {
                case None => Future(results)
                case Some( token ) => {
                    val q = query + "&pageToken=" + token
                    callYoutube( q ).map( _ ++ results ) 
                }
            }
        })
        
    }
  
  
}