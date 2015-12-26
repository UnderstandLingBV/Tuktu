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
   var prefix: String = _
   var content: String = _
   var postfix: String = _
   var encoding: String = _
    
    override def initialize(config: JsObject) 
    {
        folder = (config \ "folder").as[String]
        fileName = (config \ "fileName").as[String]
        prefix = (config \ "prefix").asOpt[String].getOrElse("")
        content = (config \ "content").as[String]
        postfix = (config \ "postfix").asOpt[String].getOrElse("")
        encoding = ( config \ "encoding" ).as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      new DataPacket(for (datum <- data.data) yield {
                val path: String = utils.evaluateTuktuString(folder, datum) + File.separator + utils.evaluateTuktuString(fileName, datum)
                val file: File = new File( path )
                
                val metadata: String = utils.evaluateTuktuString(prefix, datum) + "\n" + utils.evaluateTuktuString(content, datum) + "\n" + utils.evaluateTuktuString(postfix, datum) 
                FileUtils.write( file, metadata, utils.evaluateTuktuString(encoding, datum) ) ;
                datum
            })
    })   
}