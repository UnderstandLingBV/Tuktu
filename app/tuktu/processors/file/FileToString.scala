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
 * Converts a field to String.
 */
class FileToString(resultName: String) extends BaseProcessor(resultName) {
    /** The charset used to decode the bytes of the incoming files. */
    var charset = ""
    /** The field the file is processed in. */
    var field = ""
    var overwrite = false
    var drop = 0
    var dropRight = 0
    val separator = "\n"

    override def initialize(config: JsObject) {
        charset = (config \ "charset").asOpt[String].getOrElse("utf-8")
        field = (config \ "file_field").as[String]
        overwrite = (config \ "overwrite").asOpt[Boolean].getOrElse(false)
        drop = (config \ "drop").asOpt[Int].getOrElse(0)
        dropRight = (config \ "drop_right").asOpt[Int].getOrElse(0)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Get file contents
            val file = Source.fromFile(datum(field).asInstanceOf[Path].toFile, charset)
            val content = file.getLines.toList.drop(drop).dropRight(dropRight).mkString(separator)
            file.close

            // Parse JSON and append
            if (overwrite)
                datum + (field -> content)
            else
                datum + (resultName -> content)
        })
    })
}