package tuktu.processors

import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.FileOutputStream
import play.api.libs.iteratee.Enumeratee
import tuktu.api._
import play.api.libs.json.JsValue
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Streams data into a file and closes it when it's done
 */
class FileStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: BufferedWriter = null
    var fields = collection.mutable.Map[String, Int]()
    var fieldSep: String = null
    var lineSep: String = null
    
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // See if we need to initialize the buffered writer
       if (writer == null) {
           // Get the location of the file to write to
           val fileName = (config \ "file_name").as[String]
           val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
           
           // Get the field we need to write out
           (config \ "fields").as[List[String]].foreach {field => fields += field -> 1}
           fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
           lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")
           
           writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding))
       }

       new DataPacket(for (datum <- data.data) yield {
           // Write it
           val output = (datum collect {
                   case elem: (String, Any) if fields.contains(elem._1) => elem._2.toString
           }).mkString(fieldSep)
           
           writer.write(output + lineSep)
           
           datum
        })
    }) compose Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    })
}

/**
 * Streams data into a file and closes it when it's done
 */
class BatchedFileStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: BufferedWriter = null
    var fields = collection.mutable.Map[String, Int]()
    var fieldSep: String = null
    var lineSep: String = null
    var batchSize: Int = 1
    var batch = new StringBuilder()
    var batchCount = 0
    
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // See if we need to initialize the buffered writer
       if (writer == null) {
           // Get the location of the file to write to
           val fileName = (config \ "file_name").as[String]
           val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
           
           // Get the field we need to write out
           (config \ "fields").as[List[String]].foreach {field => fields += field -> 1}
           fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
           lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")
           
           // Get batch size
           batchSize = (config \ "batch_size").as[Int]
           
           writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding))
       }

       new DataPacket(for (datum <- data.data) yield {
           // Write it
           val output = (datum collect {
                   case elem: (String, Any) if fields.contains(elem._1) => elem._2.toString
           }).mkString(fieldSep)
           
           // Add to batch or write
           batch.append(output + lineSep)
           batchCount = batchCount + 1
           if (batchCount == batchSize) {
               writer.write(batch.toString)
               batch.clear
           }
           
           datum
        })
    }) compose Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    })
}