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
import java.io.BufferedReader
import scala.concurrent.duration.Duration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * Streams data into a file and closes it when it's done
 */
class FileStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: BufferedWriter = _
    var fields: List[String] = _
    var fieldSep: String = _
    var lineSep: String = _
    var append: Boolean = _

    override def initialize(config: JsObject) {
        // Get the location of the file to write to
        val fileName = (config \ "file_name").as[String]
        val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")

        // Get the field we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")
        append = (config \ "append").asOpt[Boolean].getOrElse(false)

        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName, append), encoding))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) {
            // Write it
            val output = (for (field <- fields if datum.contains(field)) yield datum(field).toString).mkString(fieldSep)

            writer.write(output + lineSep)
        }

        data
    }) compose Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    })
}

/**
 * Streams data into a file and rotates it
 */
class FileRotatingStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writers = collection.mutable.Map.empty[String, BufferedWriter]
    var fields: List[String] = _
    var fieldSep: String = _
    var lineSep: String = _
    var duration: Duration = _
    var fileName: String = _
    var encoding: String = _
    var started: Date = _
    var append: Boolean = _

    val dateExtractor = """\[(.*)]""".r

    override def initialize(config: JsObject) {
        // Get the location of the file to write to
        fileName = (config \ "file_name").as[String]
        encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
        duration = Duration((config \ "rotation_time").as[String])

        // Get the field we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")

        started = Calendar.getInstance.getTime
        
        append = (config \ "append").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        if (data.data.size > 0) {
            val now = Calendar.getInstance.getTime
            
            // Set the filename
            val evalFn = utils.evaluateTuktuString(fileName, data.data.head)
            if (!writers.contains(evalFn)) writers += evalFn -> getWriter(evalFn)
    
            // Check for rotation
            if (now.getTime - started.getTime > duration.toMillis) {
                val cal = Calendar.getInstance
                cal.setTimeInMillis(started.getTime + duration.toMillis)
                started = cal.getTime
                writers += evalFn -> getWriter(evalFn)
            }
    
            for (datum <- data) {
                // Write it
                val output = fields match {
                    case Nil => datum.map(_._2.toString).mkString(fieldSep)
                    case fs: List[String] => (for (field <- fs if datum.contains(field)) yield datum(field).toString).mkString(fieldSep)
                }
    
                writers(evalFn).write(output + lineSep)
            }
        }

        data
    }) compose Enumeratee.onEOF(() => {
        for (fn <- writers) closeWriter(fn._1)
    })

    def getWriter(fn: String) = {
        closeWriter(fn)
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getFileName(fn), append), encoding))
    }

    def closeWriter(fn: String) = {
        if (writers.contains(fn)) {
            try {
                writers(fn).flush
                writers(fn).close
            } catch {
                case _: Throwable => ()
            }
        }
    }

    def getFileName(fn: String) = {
        val formatter = new SimpleDateFormat(dateExtractor.findFirstMatchIn(fn).get.group(1))
        val now = formatter.format(Calendar.getInstance.getTime)
        dateExtractor.replaceAllIn(fn, now)
    }
}

/**
 * Streams binary data to a file
 */
class BinaryFileStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: FileOutputStream = _
    var fields: List[String] = _
    var fieldSep: Array[Byte] = _
    var lineSep: Array[Byte] = _

    override def initialize(config: JsObject) {
        // Get the location of the file to write to
        val fileName = (config \ "file_name").as[String]

        // Get the field we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_bytes_separator").asOpt[List[Int]].getOrElse(List()).map(_.toByte).toArray
        lineSep = (config \ "datum_bytes_separator").asOpt[List[Int]].getOrElse(List()).map(_.toByte).toArray

        writer = new FileOutputStream(fileName)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) {
            // Write it
            val output = (for (field <- fields if datum.contains(field)) yield {
                datum(field) match {
                    case a: Byte        => Array(a)
                    case a: Array[Byte] => a
                    case a: List[Byte]  => a.toArray
                    case a: Seq[Byte]   => a.toArray
                }
            }).foldLeft(Array.empty[Byte])((a, b) => a ++ fieldSep ++ b)

            writer.write(output ++ lineSep)
        }

        data
    }) compose Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    })
}

/**
 * Streams data into a file and closes it when it's done
 */
class BatchedFileStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: BufferedWriter = _
    var fields: List[String] = _
    var fieldSep: String = _
    var lineSep: String = _
    var batchSize: Int = _
    var batch = new StringBuilder()
    var batchCount: Int = _

    override def initialize(config: JsObject) {
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

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) {
            // Write it
            val output = (for (field <- fields if datum.contains(field)) yield datum(field).toString).mkString(fieldSep)

            // Add to batch or write
            batch.append(output + lineSep)
            batchCount = batchCount + 1
            if (batchCount == batchSize) {
                writer.write(batch.toString)
                batch.clear
            }
        }

        data
    }) compose Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    })
}

/**
 * Read a file in a processor
 */
class FileReaderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fileName: String = _
    var encoding: String = _
    var startLine: Int = _
    var lineSep: String = _

    override def initialize(config: JsObject) {
        // Get the location of the file to write to
        fileName = (config \ "filename").as[String]
        encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
        startLine = (config \ "start_line").asOpt[Int].getOrElse(0)
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            val fileName = utils.evaluateTuktuString(this.fileName, datum)
            val encoding = utils.evaluateTuktuString(this.encoding, datum)

            var reader: BufferedReader = null
            try {
                reader = tuktu.api.file.genericReader(fileName)(Codec(encoding))
                datum + (resultName -> Stream.continually(reader.readLine).takeWhile(_ != null).drop(startLine).mkString(lineSep))
            } finally {
                if (reader != null)
                    reader.close
            }
        }
    })
}