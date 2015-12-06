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
import tuktu.dfs.actors.TDFSWriteRequest
import play.api.libs.concurrent.Akka
import akka.pattern.ask
import scala.concurrent.duration.DurationInt
import play.api.cache.Cache
import akka.util.Timeout
import play.api.Play.current
import scala.concurrent.Await
import tuktu.dfs.actors.TDFSContentPacket
import tuktu.dfs.actors.TDFSWriterReply

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

/**
 * Writes out text to a TDFS
 */
class TDFSTextWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var writer: ActorRef = _
    
    var fields: List[String] = _
    var fieldSep: String = _
    var lineSep: String = _
    
    var filename: String = _
    var encoding: Option[String] = _
    var blockSize: Option[Int] = _

    override def initialize(config: JsObject) {
        // Get the field we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")

        // Setup fields
        filename = (config \ "file_name").as[String]
        encoding = (config \ "encoding").asOpt[String]
        blockSize = (config \ "block_size").asOpt[Int]
        
        // Set TDFS writer
        Await.result(Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new TDFSWriteRequest(
                filename,
                0,
                blockSize: Option[Int],
                false,
                encoding,
                false,
                Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
        ), timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Collect ouptut first
        val output = data.data.map(datum =>
            (for (field <- fields if datum.contains(field)) yield datum(field).toString).mkString(fieldSep)
        ).mkString(lineSep)
        
        // Send write message
        Cache.getAs[collection.mutable.Map[String, ActorRef]]("tuktu.dfs.WriterMap")
                .getOrElse(collection.mutable.Map[String, ActorRef]())(filename) ! new TDFSContentPacket(output.getBytes)
        
        data
    }) compose Enumeratee.onEOF(() => {
        writer ! new StopPacket()
    })
}