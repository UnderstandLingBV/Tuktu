package tuktu.dlib.processors

import akka.util.Timeout

import freemarker.cache._
import freemarker.template._

import java.io.StringWriter

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
 * Generates a text output based on a template and changing data
 * (using the Apache FreeMarker template engine).
 */
class TemplateProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var template: Template = _
    var field: String = _
    
    override def initialize(config: JsObject) 
    {
        // Get the configuration parameters
        val url = (config \ "template").as[String]
        val encodings = (config \ "encodings").asOpt[String].getOrElse("UTF-8")
        field = (config \ "data").as[String]
        
        // Get configuration and template
        val templateLoader: TemplateLoader = new DlibTemplateLoader() 
        val cfg = new Configuration(Configuration.VERSION_2_3_23)
        cfg.setDefaultEncoding( encodings )
        cfg.setLocalizedLookup( false )
        cfg.setTemplateLoader( templateLoader )
        template = cfg.getTemplate( url )
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        DataPacket(for (datum <- data.data) yield {
            val filler = toJava( datum( field ).asInstanceOf[Map[String,Object]] )
            val output = new StringWriter
            template.process(filler, output)
            datum + ( resultName -> output.toString )
        })
    })
    
    def toJava( m: Map[String,Object] ): java.util.Map[String, Object] =
    {
        import scala.collection.JavaConverters._
        val result = m map {case (key, value) => (key, (value match{
          case s: Seq[String] => s.asJava
          case mp: Map[String, Object] => toJava( mp )
          case _ => value
        }))}
        result.asJava
    }
}

class DlibTemplateLoader extends freemarker.cache.URLTemplateLoader
{
    override def getURL(url: String): java.net.URL =
    {
        new java.net.URL( url )
    }
}