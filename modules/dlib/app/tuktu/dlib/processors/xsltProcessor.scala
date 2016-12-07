package tuktu.dlib.processors

import akka.util.Timeout

import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import scala.io.Source

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import tuktu.api._
import tuktu.api.utils.evaluateTuktuString
/*
 * Generates a text output based on a XSL and changing XML data.
 */
class XSLTProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var transformer: Transformer = _
    var field: String = _
    
    override def initialize(config: JsObject) 
    {
        // Get the configuration parameters
        val url = (config \ "xsl").as[String]
        val encodings = (config \ "encodings").asOpt[String].getOrElse("UTF-8")
        field = (config \ "xml").as[String]
        
        // Get xsl and prepare transformer
        val xsl = Source.fromURL( url, encodings ).mkString
        val factory = TransformerFactory.newInstance()
        val xslStream = new StreamSource(new StringReader(xsl))
        transformer = factory.newTransformer(xslStream)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        DataPacket(for (datum <- data.data) yield {
            val xml =  datum( field ).asInstanceOf[String]
            val writer = new StringWriter
            val reader = new StringReader( xml )
            val in = new StreamSource( reader )
            val out = new StreamResult( writer )
            transformer.transform( in, out )
            datum + ( resultName -> writer.toString )
        })
    })
}