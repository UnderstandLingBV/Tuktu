package tuktu.processors

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

/**
 * Converts all fields to CSV
 */
class CSVProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        // Set up writer
        val sw = new StringWriter
        val csvWriter = new CSVWriter(sw, ',', '"', '\\', "")
        // Convert data to CSV
        val newData = for (datum <- data.data) yield {
            // Strings are a bit annoying here
            val stringDatum = datum.map(someVal => someVal._2.toString)
            csvWriter.writeNext(stringDatum.toArray)
            val res = sw.toString
           
            datum + (resultName -> res)
        }
        // Close
        csvWriter.close
        sw.close
        
        Future {new DataPacket(newData)}
    })
}

/**
 * Writes CSV to a file
 */
class CSVWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: CSVWriter = null
    var wroteHeaders = false
    var fields: Option[List[String]] = None
    
    override def initialize(config: JsObject) = {
        // Get the location of the file to write to
        val fileName = (config \ "file_name").as[String]
        val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")

        writer = new CSVWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding)), ';', '"', '\\')
        
        fields = (config \ "fields").asOpt[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Convert data to CSV
        for (datum <- data.data) {
            // Write out headers
            if (!wroteHeaders) {
                fields match {
                    case Some(flds) => writer.writeNext(flds.toArray)
                    case None => writer.writeNext(datum.map(elem => elem._1).toArray)
                }
                wroteHeaders = true
            }
            
            fields match {
                case Some(fields) => {
                    val values = fields.map(someVal => datum(someVal).isInstanceOf[JsString] match {
                        case true => tuktu.utils.util.JsonStringToNormalString(datum(someVal).asInstanceOf[JsString])
                        case false => datum(someVal).toString
                    })
                    writer.writeNext(values.toArray)
                }
                case None => {
                    // Strings are a bit annoying here
                    val stringDatum = datum.map(someVal => someVal._2.isInstanceOf[JsString] match {
                        case true => tuktu.utils.util.JsonStringToNormalString(someVal._2.asInstanceOf[JsString])
                        case false => someVal._2.toString
                    })
                    writer.writeNext(stringDatum.toArray)
                }
            }
        }
        
        Future {data}
    }) compose Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    })
}