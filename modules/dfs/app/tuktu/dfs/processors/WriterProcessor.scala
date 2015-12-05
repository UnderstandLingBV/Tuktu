package tuktu.dfs.processors

import java.io.FileOutputStream
import java.io.OutputStreamWriter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api._
import tuktu.api.utils
import tuktu.dfs.util.util
import tuktu.dfs.file.BufferedDFSWriter
import play.api.Play
import java.io.File
import akka.actor.ActorRef

class WriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: BufferedDFSWriter = _
    var fields: List[String] = _
    var fieldSep: String = _
    var lineSep: String = _

    val prefix = Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs")

    override def initialize(config: JsObject) {
        // Get the field we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")

        val filename = (config \ "file_name").as[String]
        val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
        val replicationFactor = (config \ "replication").asOpt[Int]

        // Get the nodes to use
        val otherNodes = utils.indexToNodeHasher(filename, replicationFactor, false)

        // See if we need to make the dirs
        val (dummy, dirs) = util.getIndex(prefix + "/" + filename)
        val dir = new File(dirs.mkString("/"))
        if (!dir.exists)
            dir.mkdirs

        // Initialize writer
        writer = new BufferedDFSWriter(
            new OutputStreamWriter(new FileOutputStream(prefix + "/" + filename), encoding),
            filename,
            encoding,
            otherNodes)
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

class TDFSWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: ActorRef = _
    var fields: List[String] = _
    var fieldSep: String = _
    var lineSep: String = _

    val prefix = Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs")

    override def initialize(config: JsObject) {
        // Get the field we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")

        val filename = (config \ "file_name").as[String]
        val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
        val replicationFactor = (config \ "replication").asOpt[Int]

        // Get the nodes to use
        val otherNodes = utils.indexToNodeHasher(filename, replicationFactor, false)

        // See if we need to make the dirs
        val (dummy, dirs) = util.getIndex(prefix + "/" + filename)
        val dir = new File(dirs.mkString("/"))
        if (!dir.exists)
            dir.mkdirs

        // Initialize writer
        writer = new BufferedDFSWriter(
            new OutputStreamWriter(new FileOutputStream(prefix + "/" + filename), encoding),
            filename,
            encoding,
            otherNodes)
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