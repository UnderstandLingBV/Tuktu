package tuktu.dlib.utils

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import scala.io.Source
import scala.io.Codec
import scala.xml._


object oaipmh 
{
    def harvest( request: String ): Elem =
    {
        val encoding: String = "UTF8"
        val src: Source = Source.fromURL( request )( Codec.apply( encoding ) )
        val txt = src.mkString
        src.close()
        XML.loadString( txt )
    }
    
    def xml2jsObject( xml: String ): JsObject =
    {
        val jsonText = org.json.XML.toJSONObject( xml ).toString( 4 )
        val jsobj: JsObject = Json.parse( jsonText ).as[JsObject]
        (jsobj \ jsobj.keys.head).as[JsObject]  
    }
}