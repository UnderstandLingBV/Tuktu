package tuktu.dlib.utils

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
}