package tuktu.dlib.processors

import java.io.File
import org.apache.commons.io.FileUtils

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._

import play.api.Play.current

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import tuktu.api._

/**
 * Serializes a metadata record to a file.
 */
class MetadataSerializationProcessor(resultName: String) extends BaseProcessor(resultName) 
{
   var folder: String = _
   var fileName: String = _
   var prefix: Option[String] = _
   var content: String = _
   var postfix: Option[String] = _
   var encoding: String = _
    
    override def initialize(config: JsObject) 
    {
        folder = (config \ "folder").as[String]
        fileName = (config \ "fileName").as[String]
        prefix = (config \ "prefix").asOpt[String]
        content = (config \ "content").as[String]
        postfix = (config \ "postfix").asOpt[String]
        encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      DataPacket(for (datum <- data.data) yield {
                val path: String = utils.evaluateTuktuString(folder, datum) + File.separator + utils.evaluateTuktuString(fileName, datum)
                val file: File = new File( path )
                
                val metadata: String = (prefix match {
                  case None => ""
                  case Some(pre) => utils.evaluateTuktuString(pre, datum) + "\n" 
                })  + utils.evaluateTuktuString(content, datum) + ( postfix match {
                  case None => ""
                  case Some(post) => "\n" + utils.evaluateTuktuString(post, datum)
                }) 
                FileUtils.write( file, metadata, utils.evaluateTuktuString(encoding, datum) ) ;
                datum
            })
    })   
}