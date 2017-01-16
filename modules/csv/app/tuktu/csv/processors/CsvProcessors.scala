package tuktu.csv.processors

import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.StringReader
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.opencsv.CSVWriter
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils
import com.opencsv.CSVReader

/**
 * Converts all fields into a CSV string
 */
class CSVStringProcessor(resultName: String) extends BaseProcessor(resultName) {
    var separator: Char = _
    var quote: Char = _
    var escape: Char = _

    override def initialize(config: JsObject) {
        separator = (config \ "separator").asOpt[String].flatMap { _.headOption }.getOrElse(';')
        quote = (config \ "quote").asOpt[String].flatMap { _.headOption }.getOrElse('\"')
        escape = (config \ "escape").asOpt[String].flatMap { _.headOption }.getOrElse('\\')
    }

    override def processor: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Convert data to CSV
        for (datum <- data) yield {
            // Set up writers
            val sw = new StringWriter
            val csvWriter = new CSVWriter(sw, separator, quote, escape, "")

            // Sort the datum by key to guarantee a consistent order for all similar data packets,
            // before mapping to their value's string representation
            val stringDatum = datum.toArray.sortBy(_._1).map(_._2.toString)
            csvWriter.writeNext(stringDatum)
            val res = sw.toString

            // Close the underlying stream writer flushing any buffered content
            csvWriter.close

            // Append result
            datum + (resultName -> res)
        }
    })
}

/**
 * Reads a field as CSV into a map that is put into the datapacket
 */
class CSVReaderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var headers: Option[Seq[String]] = _
    var headersFromFirst: Boolean = _
    var usedHeadersFromFirst: Boolean = false
    var field: String = _

    var separator: Char = _
    var quote: Char = _
    var escape: Char = _

    var removeOriginal: Boolean = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        // Get headers
        headers = (config \ "headers").asOpt[Seq[String]]
        headersFromFirst = (config \ "headers_from_first").asOpt[Boolean].getOrElse(false)

        separator = (config \ "separator").asOpt[String].flatMap { _.headOption }.getOrElse(';')
        quote = (config \ "quote").asOpt[String].flatMap { _.headOption }.getOrElse('\"')
        escape = (config \ "escape").asOpt[String].flatMap { _.headOption }.getOrElse('\\')

        removeOriginal = (config \ "remove_original").asOpt[Boolean].getOrElse(false)
    }

    override def processor: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Get lines to mimic a CSV file
        for (datum <- data) yield {
            val value = datum(field).toString

            // Read CSV and process
            val reader = new CSVReader(new StringReader(value), separator, quote, escape)
            val line = reader.readNext
            reader.close

            // Check if these are our headers
            if (headers.isEmpty) {
                if (headersFromFirst) {
                    headers = Some(line)
                    usedHeadersFromFirst = true
                } else headers = Some(for (i <- 0 until line.size) yield i.toString)
            }

            // Optionally remove original field, and add to result
            {
                if (removeOriginal) datum - field
                else datum
            } ++ {
                if (headers.get.size != line.size)
                    throw new NoSuchElementException("Header size (" + headers.get.size + ") and line size (" + line.size + ") do no match.")
                headers.get.zip(line).toMap
            }
        }
    }) compose Enumeratee.drop(if (usedHeadersFromFirst) 1 else 0)
}

/**
 * Writes CSV to a file
 */
class CSVWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writers = scala.collection.mutable.Map[String, CSVWriter]()
    var headers = scala.collection.mutable.Map[String, Array[String]]()
    var fields: Option[Array[String]] = _
    var fileName, encoding: String = _
    var separator, quote, escape: Char = _

    override def initialize(config: JsObject) {
        // Get the location of the file to write to
        fileName = (config \ "file_name").as[String]
        encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")

        separator = (config \ "separator").asOpt[String].flatMap { _.headOption }.getOrElse(';')
        quote = (config \ "quote").asOpt[String].flatMap { _.headOption }.getOrElse('\"')
        escape = (config \ "escape").asOpt[String].flatMap { _.headOption }.getOrElse('\\')

        fields = (config \ "fields").asOpt[Array[String]]
    }

    override def processor: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Convert data to CSV
        for (datum <- data.data) {
            val evaluated_fileName = utils.evaluateTuktuString(fileName, datum)

            // Check if headers are already set
            if (!headers.contains(evaluated_fileName)) {
                // Get headers from fields or from the datum keys
                headers += evaluated_fileName -> {
                    fields match {
                        case Some(flds) => flds
                        case None       => datum.map(elem => elem._1).toArray
                    }
                }

                // Create new writer for the fileName
                writers += evaluated_fileName -> new CSVWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(evaluated_fileName), encoding)), separator, quote, escape)

                // Write out headers
                writers(evaluated_fileName).writeNext(headers(evaluated_fileName))
            }

            val values = headers(evaluated_fileName).map(header =>
                // JsStrings are a bit annoying here, because toString includes surrounding quotes "" for them
                if (datum(header).isInstanceOf[JsString])
                    datum(header).asInstanceOf[JsString].value
                else
                    datum(header).toString)

            writers(evaluated_fileName).writeNext(values)
        }

        Future { data }
    }) compose Enumeratee.onEOF(() => {
        for (writer <- writers) {
            writer._2.flush
            writer._2.close
        }
    })
}

/**
 * Parses a fixed-width delimited file where each field always has a specific size
 */
class FixedWidthProcessor(resultName: String) extends BaseProcessor(resultName) {
    var headers: Option[List[String]] = _
    var field: String = _
    var widths: List[Int] = _
    var flatten: Boolean = _
    var numberHeaders: Seq[String] = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        widths = (config \ "widths").as[List[Int]]
        headers = (config \ "headers").asOpt[List[String]]
        flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
        numberHeaders = (0 to widths.size).map(_.toString)
    }

    def substringFetch(curWidths: List[Int], string: String): List[String] = curWidths match {
        case Nil            => List(string)
        case width :: trail => string.take(width) :: substringFetch(trail, string.drop(width))
    }

    override def processor: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            // Get the field to split on
            val values = substringFetch(widths, datum(field).toString)

            // Merge with headers or add to DP
            if (flatten) headers match {
                case Some(hdrs) => datum ++ hdrs.zip(values).toMap
                case None       => datum ++ numberHeaders.zip(values).toMap
            }
            else datum + (resultName -> (headers match {
                case Some(hdrs) => headers.zip(values).toMap
                case None       => values
            }))
        }
    })
}