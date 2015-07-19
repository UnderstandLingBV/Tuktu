package tuktu.dfs.processors

import java.io.FileOutputStream
import java.io.OutputStreamWriter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api._
import tuktu.api.utils
import tuktu.dfs.file.BufferedDFSWriter

class WriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: BufferedDFSWriter = _
    var fields: List[String] = _
    var fieldSep: String = _
    var lineSep: String = _
    
    override def initialize(config: JsObject) {
        // Get the field we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")
        
        val filename = (config \ "filename").as[String]
        val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
        val replicationFactor = (config \ "replication").asOpt[Int]
        
        // Get the nodes to use
        val otherNodes = utils.indexToNodeHasher(filename, replicationFactor, false)
        
        // Initialize writer
        writer = new BufferedDFSWriter(
                new OutputStreamWriter(new FileOutputStream(filename), encoding),
                filename,
                encoding,
                otherNodes
        )
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Write it
            val output = (for (field <- fields if datum.contains(field)) yield datum(field).toString).mkString(fieldSep)
            writer.write(output + lineSep)

            datum
        })
    }) compose Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    })
}