package tuktu.dlib.utils

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.io.Codec
import scala.xml._


/**
 * Some utility functions to automate repetitive OAI-PMH tasks
 */
object oaipmh 
{
    /**
     * Sends a request to an OAI-PMH target
     * @param request: A valid OAI-PMH request 
     * @return An OAI-PMH response
     */
    def harvest( request: String ): Elem =
    {
        val encoding: String = "UTF8"
        val src: Source = Source.fromURL( request )( Codec.apply( encoding ) )
        val txt = src.mkString
        src.close()
        XML.loadString( txt )
    }
    
    /**
     * Turns XML (as a string) into a JSON object (as a JsObject)  
     * @param xml: The XML document to convert 
     * @return A JSON object equivalent of the XML provided 
     */
    def xml2jsObject( xml: String ): JsObject =
    {
        val jsonText = org.json.XML.toJSONObject( xml ).toString( 4 )
        val jsobj: JsObject = Json.parse( jsonText ).as[JsObject]
        (jsobj \ jsobj.keys.head).as[JsObject]  
    }
    
    /**
     * A non-blocking version of the harvest method Sends a request to an OAI-PMH target
     * @param request: A valid OAI-PMH request 
     * @return A future OAI-PMH response
     */
    def fharvest(url: String): Future[scala.xml.Elem] = 
    {
        WS.url(url).get().map { response => response.xml }
    }
    
}