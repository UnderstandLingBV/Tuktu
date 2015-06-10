package tuktu.csv.processors

import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import au.com.bytecode.opencsv.CSVWriter
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import au.com.bytecode.opencsv.CSVReader
import java.io.StringReader

/**
 * Converts all fields into a CSV string
 */
class CSVStringProcessor(resultName: String) extends BaseProcessor(resultName) {
    var separator = ';'
    var quote = '"'
    var escape = '\\'

    override def initialize(config: JsObject) = {
        separator = (config \ "separator").asOpt[String].getOrElse(";").head
        quote = (config \ "quote").asOpt[String].getOrElse("\"").head
        escape = (config \ "escape").asOpt[String].getOrElse("\\").head
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Set up writer
        val sw = new StringWriter
        val csvWriter = new CSVWriter(sw, separator, quote, escape, "")

        // Convert data to CSV
        val newData = for (datum <- data.data) yield {
            // Strings are a bit annoying here
            val stringDatum = datum.map(someVal => someVal._2.toString)
            csvWriter.writeNext(stringDatum.toArray)
            val res = sw.toString

            // See if we need to append headers or not
            datum + (resultName -> res)
        }

        // Close
        csvWriter.close
        sw.close

        Future { new DataPacket(newData) }
    })
}

/**
 * Reads a field as CSV into a map that is put into the datapacket
 */
class CSVReaderProcessor(resultName: String) extends BaseProcessor(resultName) {
    var headers: List[String] = null
    var headersFromFirst = false
    var field = ""

    var separator = ';'
    var quote = '"'
    var escape = '\\'

    var removeOriginal = false

    override def initialize(config: JsObject) = {
        field = (config \ "field").as[String]
        // Get headers
        headers = (config \ "headers").asOpt[List[String]].getOrElse(null)
        headersFromFirst = (config \ "headers_from_first").asOpt[Boolean].getOrElse(false)

        separator = (config \ "separator").asOpt[String].getOrElse(",").head
        quote = (config \ "quote").asOpt[String].getOrElse("\"").head
        escape = (config \ "escape").asOpt[String].getOrElse("\\").head

        removeOriginal = (config \ "remove_original").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket({
            // See if this is the first one
            val first = headers == null

            // Get lines to mimic a CSV file
            (for (datum <- data.data) yield {
                val value = datum(field).toString

                // Read CSV and process
                val reader = new CSVReader(new StringReader(value), separator, quote, escape)
                val line = reader.readNext
                reader.close

                // Check if these are our headers
                if (headers == null) {
                    if (headersFromFirst) headers = line.toList
                    else headers = (for (i <- 0 to line.size - 1) yield i.toString).toList
                }

                // Add to result
                {
                    if (removeOriginal) datum - field
                    else datum
                } ++ {
                    val toAdd = headers.zip(line.toList).toMap
                    toAdd
                }
            }).drop(first match {
                case true if headersFromFirst => 1
                case _ => 0
            })
        })
    })
}

/**
 * Writes CSV to a file
 */
class CSVWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: CSVWriter = null
    var headers: Array[String] = null
    var fields: Option[List[String]] = None

    override def initialize(config: JsObject) = {
        // Get the location of the file to write to
        val fileName = (config \ "file_name").as[String]
        val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")

        val separator = (config \ "separator").asOpt[String].getOrElse(";").head
        val quote = (config \ "quote").asOpt[String].getOrElse("\"").head
        val escape = (config \ "escape").asOpt[String].getOrElse("\\").head
        writer = new CSVWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding)), separator, quote, escape)

        fields = (config \ "fields").asOpt[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Convert data to CSV
        for (datum <- data.data) {
            // Get and write out headers
            if (headers == null) {
                headers = fields match {
                    case Some(flds) => flds.toArray
                    case None => datum.map(elem => elem._1).toArray
                }

                writer.writeNext(headers)
            }

            val values = headers.map(header =>
                // JsStrings are a bit annoying here
                if (datum(header).isInstanceOf[JsString])
                    datum(header).asInstanceOf[JsString].value
                else
                    datum(header).toString)

            writer.writeNext(values)
        }

        Future { data }
    }) compose Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    })
}