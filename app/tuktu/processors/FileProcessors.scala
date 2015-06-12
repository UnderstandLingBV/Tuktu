package tuktu.processors

import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils
import scala.io.Codec
import scala.io.Source

/**
 * Streams data into a file and closes it when it's done
 */
class FileStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: BufferedWriter = null
    var fields = List[String]()
    var fieldSep: String = null
    var lineSep: String = null

    override def initialize(config: JsObject) = {
        // Get the location of the file to write to
        val fileName = (config \ "file_name").as[String]
        val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")

        // Get the field we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")

        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        Future {
            new DataPacket(for (datum <- data.data) yield {
                // Write it
                val output = (for (field <- fields if datum.contains(field)) yield datum(field).toString).mkString(fieldSep)

                writer.write(output + lineSep)

                datum
            })
        }
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
    var fields = List[String]()
    var fieldSep: String = null
    var lineSep: String = null
    var batchSize: Int = 1
    var batch = new StringBuilder()
    var batchCount = 0

    override def initialize(config: JsObject) = {
        // Get the location of the file to write to
        val fileName = (config \ "file_name").as[String]
        val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")

        // Get the field we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")

        // Get batch size
        batchSize = (config \ "batch_size").as[Int]

        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        Future {
            new DataPacket(for (datum <- data.data) yield {
                // Write it
                val output = (for (field <- fields if datum.contains(field)) yield datum(field).toString).mkString(fieldSep)

                // Add to batch or write
                batch.append(output + lineSep)
                batchCount = batchCount + 1
                if (batchCount == batchSize) {
                    writer.write(batch.toString)
                    batch.clear
                }

                datum
            })
        }
    }) compose Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    })
}

class FileReaderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fileName = ""
    var encoding = "utf-8"
    var startLine = 0
    var lineSep: String = _

    override def initialize(config: JsObject) = {
        // Get the location of the file to write to
        fileName = (config \ "filename").as[String]
        encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
        startLine = (config \ "start_line").asOpt[Int].getOrElse(0)
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for (datum <- data.data) yield {
            val fileName = utils.evaluateTuktuString(this.fileName, datum)
            val encoding = utils.evaluateTuktuString(this.encoding, datum)

            val reader = tuktu.api.file.genericReader(fileName)(Codec.apply(encoding))
            datum + (resultName -> Stream.continually(reader.readLine()).takeWhile(_ != null).mkString(lineSep))
        })
    })
}