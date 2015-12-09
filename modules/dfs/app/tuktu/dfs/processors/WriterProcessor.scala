package tuktu.dfs.processors

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import tuktu.dfs.actors.TDFSContentPacket
import tuktu.dfs.actors.TDFSInitiateRequest

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
        // Get the fields we need to write out
        fields = (config \ "fields").as[List[String]]
        fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
        lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")

        // Get file parameters
        filename = (config \ "file_name").as[String]
        encoding = (config \ "encoding").asOpt[String]
        blockSize = (config \ "block_size").asOpt[Int]
        
        // Set TDFS writer
        writer = Await.result(Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new TDFSInitiateRequest(
            filename, blockSize, false, encoding
        ), timeout.duration).asInstanceOf[ActorRef]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Collect ouptut first
        val output = data.data.map(datum =>
            (for (field <- fields if datum.contains(field)) yield datum(field).toString).mkString(fieldSep)
        ).mkString(lineSep)
        
        // Send message
        writer ! new TDFSContentPacket(output.getBytes)
        
        data
    }) compose Enumeratee.onEOF(() => {
        writer ! new StopPacket()
    })
}

/**
 * Writes a binary field out to TDFS
 */
class TDFSBinaryWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var writer: ActorRef = _
    
    var field: String = _
    
    var filename: String = _
    var blockSize: Option[Int] = _

    override def initialize(config: JsObject) {
        // Get the field we need to write out
        field = (config \ "fields").as[String]
        
        // Setup fields
        filename = (config \ "file_name").as[String]
        blockSize = (config \ "block_size").asOpt[Int]
        
        // Set TDFS writer
        writer = Await.result(Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new TDFSInitiateRequest(
            filename, blockSize, false, None
        ), timeout.duration).asInstanceOf[ActorRef]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Collect ouptut first
        // TODO: Support more types and make sure 
        val output = data.data.map(datum =>
            datum(field) match {
                case a: Byte => Array(a)
                case a: String => a.getBytes
                case a: Int => Array(a.byteValue)
                case a: Long => a.toHexString.getBytes
                case a: Array[Byte] => a
                case a: Seq[Byte] => a.toArray
                case a: Any => a.toString.getBytes
            }
        ).flatten.toArray
        
        // Send message
        writer ! new TDFSContentPacket(output)
        
        data
    }) compose Enumeratee.onEOF(() => {
        writer ! new StopPacket()
    })
}