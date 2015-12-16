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
import tuktu.api.utils
import au.com.bytecode.opencsv.CSVReader
import java.io.StringReader

/**
 * Converts all fields into a CSV string
 */
class CSVStringProcessor(resultName: String) extends BaseProcessor(resultName) {
    var separator = ';'
    var quote = '"'
    var escape = '\\'

    override def initialize(config: JsObject) {
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
            // Sort the datum by key to guarantee a consistent order for all similar data packets,
            // before mapping to their value's string representation
            val stringDatum = datum.toArray.sortBy(_._1).map(_._2.toString)
            csvWriter.writeNext(stringDatum)
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

    override def initialize(config: JsObject) {
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
    }) compose Enumeratee.filter((data: DataPacket) => {
        !data.data.isEmpty
    })
}

/**
 * Writes CSV to a file
 */
class CSVWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writers = scala.collection.mutable.Map[String, CSVWriter]()
    var headers = scala.collection.mutable.Map[String, Array[String]]()
    var fields: Option[Array[String]] = None
    var fileName, encoding: String = _
    var separator, quote, escape: Char = _

    override def initialize(config: JsObject) {
        // Get the location of the file to write to
        fileName = (config \ "file_name").as[String]
        encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")

        separator = (config \ "separator").asOpt[String].getOrElse(";").head
        quote = (config \ "quote").asOpt[String].getOrElse("\"").head
        escape = (config \ "escape").asOpt[String].getOrElse("\\").head

        fields = (config \ "fields").asOpt[Array[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Convert data to CSV
        for (datum <- data.data) {
            val evaluated_fileName = utils.evaluateTuktuString(fileName, datum)

            // Check if headers are already set
            if (!headers.contains(evaluated_fileName)) {
                // Get headers from fields or from the datum keys
                headers += evaluated_fileName -> {
                    fields match {
                        case Some(flds) => flds
                        case None => datum.map(elem => elem._1).toArray
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