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

case class TDFSWriteRequest(
        filename: String,
        filepart: Int,
        blockSize: Option[Int],
        binary: Boolean,
        encoding: Option[String],
        nodeSet: Boolean
)
case class TDFSWriterReply(
        writer: ActorRef
)
case class TDFSContentPacket(
        content: Array[Byte]
)

/**
 * Daemon actor that handles write and read requests
 */
class TDFSDaemon extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var nodeOffset = 0
    
    def createWriter(twr: TDFSWriteRequest) = {
        // Get block size
        val blockSize = twr.blockSize match {
            case None => Cache.getAs[Int]("dfs.blocksize").getOrElse(128)
            case Some(size) => size
        }
        
        // Set up the writer actor
        val partname = twr.filename + "-part" + twr.filepart
        val writer = {
            if (twr.binary)
                Akka.system.actorOf(Props(classOf[TDFSWriterActor], partname, twr.filepart, blockSize),
                    name = "tuktu.dfs.Writer.binary." + partname)
            else
                Akka.system.actorOf(Props(classOf[TextTDFSWriterActor], partname, twr.encoding.getOrElse("utf-8"),
                    twr.filepart, blockSize),
                    name = "tuktu.dfs.Writer.text." + partname)
        }
        
        // TODO: Maybe keep track of writers per file name in Cache ?

        // Send back the actor ref to the sender
        writer
    }
    
    def receive() = {
        case twr: TDFSWriteRequest => {
            if (twr.nodeSet)
                // Node is already set (we are the node), we don't need to pick one
                sender ! new TDFSWriterReply(createWriter(twr))
            else {
                // Get all DFS nodes
                val dfsNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
                val thisNode = Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
                
                // Determine the node to write to, based on file part
                val node = dfsNodes.toList(nodeOffset % dfsNodes.size)
                nodeOffset = (nodeOffset + 1) % dfsNodes.size
                
                // Check if we are the node that needs to write or not
                if (node._1 == thisNode) sender ! new TDFSWriterReply(createWriter(twr))
                else {
                    // Ask the other node for a writer
                    val otherNode = node._2
                    val location = "akka.tcp://application@" + otherNode.host  + ":" + otherNode.akkaPort + "/user/tuktu.dfs.Daemon"
                    
                    // Create writer and send back
                    val fut = (Akka.system.actorSelection(location) ? new TDFSWriteRequest(
                            twr.filename,
                            twr.filepart,
                            twr.blockSize,
                            twr.binary,
                            twr.encoding,
                            true
                    )).asInstanceOf[Future[TDFSWriterReply]]
                    fut.map(twreply => sender ! twreply)
                }
            }
        }
    }
}

/**
 * Actual writer actor, a single instance will be created for writing each block of a file
 */
class TDFSWriterActor(filename: String, filepart: Int, blockSize: Int) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Create the file
    val writer = new FileOutputStream(filename)
    // Keep track of bytes written
    var byteCount: Long = 0
    
    def receive() = {
        case sp: StopPacket => {
            writer.close()
            self ! PoisonPill
        }
        case tcp: TDFSContentPacket => {
            // Write bytes
            if (byteCount + tcp.content.length > blockSize) {
                // Write the part we can still manage
                writer.write(tcp.content, 0, (blockSize * 1024L - byteCount).toInt)
                
                // Close this block
                byteCount = 0
                writer.close()
                
                // Get remainder
                val remainder = tcp.content.slice((blockSize * 1024L - byteCount).toInt, tcp.content.length)
                // Open a new writer
                val fut = (Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new TDFSWriteRequest(
                            filename,
                            filepart + 1,
                            Some(blockSize),
                            true,
                            None,
                            false
                    )).asInstanceOf[Future[TDFSWriterReply]]
                
                // Send the new writer the remaining content and return writer actorref to the sender
                fut.onSuccess {
                    case twreply: TDFSWriterReply => {
                        twreply.writer ! new TDFSContentPacket(remainder)
                        sender ! twreply.writer
                        
                        // We can stop now
                        self ! new StopPacket
                    }
                }
                fut.onFailure {
                    case a: Any => {
                        log.error(a, "[TDFS] - Failed to obtain a new writer in a timely manner.")
                        
                        // We can stop now
                        self ! new StopPacket
                    }
                }
            }
            else {
                // First send back actor ref
                sender ! new TDFSWriterReply(self)
                // Write entirely
                writer.write(tcp.content)
                byteCount += tcp.content.length
            }
        }
    }
}

/**
 * Writes text to a file, like the binary writer, but now as text
 */
class TextTDFSWriterActor(filename: String, encoding: String, filepart: Int, blockSize: Int) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Create the file
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), encoding))
    // Keep track of bytes written
    var byteCount: Long = 0
    
    def receive() = {
        case sp: StopPacket => {
            writer.close()
            self ! PoisonPill
        }
        case tcp: TDFSContentPacket => {
            // Write bytes
            if (byteCount + tcp.content.length > blockSize) {
                // Write the part we can still manage
                val len = Math.floor((blockSize * 1024L - byteCount).toDouble / 8.0).toInt
                val newContent = tcp.content.slice(0, len)
                writer.write(new String(newContent, encoding), 0, len)
                
                // Close this block
                byteCount = 0
                writer.close()
                
                // Get remainder
                val remainder = tcp.content.slice((blockSize * 1024L - byteCount).toInt, tcp.content.length)
                // Open a new writer
                val fut = (Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new TDFSWriteRequest(
                            filename,
                            filepart + 1,
                            Some(blockSize),
                            false,
                            Some(encoding),
                            false
                    )).asInstanceOf[Future[TDFSWriterReply]]
                
                // Send the new writer the remaining content and return writer actorref to the sender
                fut.onSuccess {
                    case twreply: TDFSWriterReply => {
                        twreply.writer ! new TDFSContentPacket(remainder)
                        sender ! twreply.writer
                        
                        // We can stop now
                        self ! new StopPacket
                    }
                }
                fut.onFailure {
                    case a: Any => {
                        log.error(a, "[TDFS] - Failed to obtain a new writer in a timely manner.")
                        
                        // We can stop now
                        self ! new StopPacket
                    }
                }
            }
            else {
                // First send back actor ref
                sender ! new TDFSWriterReply(self)
                // Write entirely
                writer.write(new String(tcp.content, encoding))
                byteCount += tcp.content.length
            }
        }
    }
}