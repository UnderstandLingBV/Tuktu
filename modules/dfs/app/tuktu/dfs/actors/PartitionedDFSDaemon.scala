package tuktu.dfs.actors

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.Props
import play.api.libs.concurrent.Akka
import play.api.Play.current
import java.io.FileOutputStream
import tuktu.api.StopPacket
import play.api.cache.Cache
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import akka.actor.ActorRef
import tuktu.api.ClusterNode
import akka.pattern.ask
import akka.actor.Identify
import akka.actor.ActorIdentity
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.PoisonPill
import akka.event.Logging
import java.io.File
import tuktu.dfs.util.util
import play.api.Play

case class TDFSInitiateRequest(
        filename: String,
        blockSize: Option[Int],
        binary: Boolean,
        encoding: Option[String]
)
case class TDFSWriteRequest(
        filename: String,
        part: Int,
        blockSize: Option[Int],
        binary: Boolean,
        encoding: Option[String]
)
case class TDFSContentPacket(
        content: Array[Byte]
)

/**
 * Daemon actor that handles write and read requests
 */
class TDFSDaemon extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    def receive() = {
        case tir: TDFSInitiateRequest => {
            // Set up the writer actor that will take care of the rest and return the ref to sender
            sender ! Akka.system.actorOf(Props(classOf[WriterDaemon], tir),
                    name = "tuktu.dfs.WriterDaemon." + tir.filename)
        }
        case twr: TDFSWriteRequest => {
            // Create the writer and send the actor ref back
            
            // Text or binary?
            sender ! {
                if (twr.binary)
                    Akka.system.actorOf(Props(classOf[BinaryTDFSWriterActor], twr),
                            name = "tuktu.dfs.Writer.binary." + twr.filename + ".part" + twr.part)
                else
                    Akka.system.actorOf(Props(classOf[TextTDFSWriterActor], twr),
                            name = "tuktu.dfs.Writer.text." + twr.filename + ".part" + twr.part)
            }
                
        }
    }
}

/**
 * Daemon that is always alive on the node the TDFS file is written from, to make sure there is always an endpoint. This daemon
 * acts as a proxy to the actual (remote) writer actors
 */
class WriterDaemon(tir: TDFSInitiateRequest) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    val homeAddress = Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
    
    // Determine initial node to write to
    var nodeOffset = {
        val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
        // Pick random offset
        scala.util.Random.nextInt(clusterNodes.size)
    }
    
    // Bookkeeping
    var currentPart = 0
    var writer: ActorRef = _
    var byteCount: Int = 0
    val blockSize = tir.blockSize match {
        case Some(s) => s
        case _ => Cache.getAs[Int]("dfs.blocksize").getOrElse(64)
    }
    
    /**
     * Creates the next writer
     */
    def createWriter() = {
        // Pick node to use
        val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
        val node = clusterNodes.toList(nodeOffset % clusterNodes.size)
        nodeOffset = (nodeOffset + 1) % clusterNodes.size
        
        // Ask the TDFS Daemon for a writer
        val otherNode = node._2
        val location = if (otherNode.host == homeAddress) "user/tuktu.dfs.Daemon"
            else
                "akka.tcp://application@" + otherNode.host  + ":" + otherNode.akkaPort + "/user/tuktu.dfs.Daemon"
        
        // Send request
        writer = Await.result((Akka.system.actorSelection(location) ? new TDFSWriteRequest(
                tir.filename,
                currentPart,
                tir.blockSize,
                tir.binary,
                tir.encoding
        )).asInstanceOf[Future[ActorRef]], timeout.duration)
    }
    
    createWriter
    
    def receive() = {
        case tcp: TDFSContentPacket => {
            println("Daemon Writer received content")
            // Check if we can still add to the current block
            if (byteCount + tcp.content.length > blockSize * 1024 * 2014) {
                println("-- Splitting and writing")
                // We are going to be full after this block, make sure we forward the part that can still be written
                writer ! new TDFSContentPacket(tcp.content.slice(0, blockSize * 1024 * 1024 - byteCount))
                val remainder = new TDFSContentPacket(tcp.content.slice(blockSize * 1024 * 1024 - byteCount, tcp.content.length))
                
                // Toss away the old writer
                writer ! new StopPacket
                
                // Create the new writer
                byteCount = 0
                currentPart = currentPart + 1
                createWriter
            } else {
                println("-- Entirely writing")
                // Just write the entire thing
                writer ! tcp
                byteCount += tcp.content.length
            }
        }
        case sp: StopPacket => {
            writer ! sp
            self ! PoisonPill
        }
    }
}

/**
 * Actual writer actor, a single instance will be created for writing each block of a file
 */
class BinaryTDFSWriterActor(twr: TDFSWriteRequest) extends Actor with ActorLogging {
    val prefix = Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs")
    
    // Create dirs if required
    val fullname = prefix + "/" + twr.filename + ".part-" + twr.part
    val (dummy, dirs) = util.getIndex(fullname)
    val dir = new File(dirs.mkString("/"))
    if (!dir.exists)
        dir.mkdirs

    // Create the actual writer
    val writer = new FileOutputStream(fullname)
    
    def receive() = {
        case sp: StopPacket => {
            writer.close()
            self ! PoisonPill
        }
        case tcp: TDFSContentPacket =>
            // Write out
            writer.write(tcp.content)
    }
}

/**
 * Writes text to a file, like the binary writer, but now as text
 */
class TextTDFSWriterActor(twr: TDFSWriteRequest) extends Actor with ActorLogging {
    val prefix = Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs")
    
    // Create dirs if required
    val fullname = prefix + "/" + twr.filename + ".part-" + twr.part
    val (dummy, dirs) = util.getIndex(fullname)
    val dir = new File(dirs.mkString("/"))
    if (!dir.exists)
        dir.mkdirs
    
    // Encoding
    val encoding = twr.encoding.getOrElse("utf-8")
        
    // Create the file
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fullname), encoding))
    
    // Keep track of bytes written
    var byteCount: Int = 0
    
    def receive() = {
        case sp: StopPacket => {
            println("+++Text writer is closing")
            writer.close()
            self ! PoisonPill
        }
        case tcp: TDFSContentPacket => {
            println("+++Text writer is writing content")
            // Write out
            writer.write(new String(tcp.content, encoding))
        }
    }
}