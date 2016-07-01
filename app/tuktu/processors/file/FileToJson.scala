package tuktu.processors.file

import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import scala.io.Source

/**
 * Converts a field to JSON.
 */
class FileToJson(resultName: String) extends BaseProcessor(resultName) {
    /** The charset used to decode the bytes of the incoming files. */
    var charset: String = _
    /** The field the file is processed in. */
    var field: String = _

    override def initialize(config: JsObject) {
        charset = (config \ "charset").asOpt[String].getOrElse("utf-8")
        field = (config \ "file_field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            // Get file contents
            val file = Source.fromFile(datum(field).asInstanceOf[Path].toFile, charset)
            val content = file.getLines.mkString
            file.close

            // Parse JSON and append
            datum + (resultName -> Json.parse(content))
        }
    })
}