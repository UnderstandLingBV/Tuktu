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

case class TDFSWriteRequest(
        filename: String,
        filepart: Int,
        blockSize: Option[Int],
        binary: Boolean,
        encoding: Option[String],
        nodeSet: Boolean,
        sourceNode: String
)
case class TDFSWriterReply(
        writer: ActorRef
)
case class TDFSWriterUpdate(
        filename: String,
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
    val prefix = Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs")
    val homeAddress = Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
    
    def createWriter(twr: TDFSWriteRequest) = {
        // Get block size
        val blockSize = twr.blockSize match {
            case None => Cache.getAs[Int]("dfs.blocksize").getOrElse(128)
            case Some(size) => size
        }
        
        // Check if we need to make the dirs
        val blockFilename = prefix + "/" + twr.filename + ".part" + twr.filepart
        val (dummy, dirs) = util.getIndex(blockFilename)
        val dir = new File(dirs.mkString("/"))
        if (!dir.exists)
            dir.mkdirs
        
        // Set up the writer actor
        val writer = {
            if (twr.binary)
                Akka.system.actorOf(Props(classOf[TDFSWriterActor], twr.filename, blockFilename,
                    twr.filepart, blockSize, twr.sourceNode),
                    name = "tuktu.dfs.Writer.binary." + dirs.mkString(".") + twr.filename + "_part" + twr.filepart)
            else
                Akka.system.actorOf(Props(classOf[TextTDFSWriterActor], twr.filename, blockFilename, twr.encoding.getOrElse("utf-8"),
                    twr.filepart, blockSize, twr.sourceNode),
                    name = "tuktu.dfs.Writer.text." + dirs.mkString(".") + twr.filename + "_part" + twr.filepart)
        }
        
        // Add writer to cache
        Cache.getAs[collection.mutable.Map[String, ActorRef]]("tuktu.dfs.WriterMap")
            .getOrElse(collection.mutable.Map[String, ActorRef]()) += twr.filename -> writer
            
        writer
    }
    
    def receive() = {
        case twu: TDFSWriterUpdate => {
            Cache.getAs[collection.mutable.Map[String, ActorRef]]("tuktu.dfs.WriterMap")
                .getOrElse(collection.mutable.Map[String, ActorRef]()) += twu.filename -> twu.writer
        }
        case twr: TDFSWriteRequest => {
            if (twr.nodeSet)
                // Node is already set (we are the node), we don't need to pick one
                sender ! new TDFSWriterReply(createWriter(twr))
            else {
                // Get all DFS nodes
                val dfsNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
                
                // Determine the node to write to, based on file part
                val node = dfsNodes.toList(nodeOffset % dfsNodes.size)
                nodeOffset = (nodeOffset + 1) % dfsNodes.size
                
                // Check if we are the node that needs to write or not
                if (node._1 == homeAddress) sender ! new TDFSWriterReply(createWriter(twr))
                else {
                    // Ask the other node for a writer
                    val otherNode = node._2
                    val location = "akka.tcp://application@" + otherNode.host  + ":" + otherNode.akkaPort + "/user/tuktu.dfs.Daemon"
                    
                    // Create writer
                    val twreply = Await.result((Akka.system.actorSelection(location) ? new TDFSWriteRequest(
                            twr.filename,
                            twr.filepart,
                            twr.blockSize,
                            twr.binary,
                            twr.encoding,
                            true,
                            twr.sourceNode
                    )).asInstanceOf[Future[TDFSWriterReply]], timeout.duration)
                    
                    // Check if we are the source node
                    if (twr.sourceNode == homeAddress)
                        // Add to cache
                        Cache.getAs[collection.mutable.Map[String, ActorRef]]("tuktu.dfs.WriterMap")
                            .getOrElse(collection.mutable.Map[String, ActorRef]()) += twr.filename -> twreply.writer
                    else {
                        val sourceNode = dfsNodes(twr.sourceNode)
                        // Send actor ref to real source node
                        Akka.system.actorSelection("akka.tcp://application@" + sourceNode.host + ":" + sourceNode.akkaPort
                                + "/user/tuktu.dfs.Daemon") ! new TDFSWriterUpdate(
                                twr.filename, twreply.writer
                        )
                    }
                    
                    sender ! new TDFSWriterReply(twreply.writer)
                }
            }
        }
    }
}

/**
 * Daemon that is always alive on the node the TDFS file is written from, to make sure there is always an endpoint. This daemon
 * acts as a proxy to the actual (remote) writer actors
 */
class WriterDaemon(filename: String, fullname: String, filepart: Int, blockSize: Int, sourceNode: String) extends Actor with ActorLogging {
    var writer: ActorRef = _
    def receive() = {
        case tcp: TDFSContentPacket => {
            
        }
    }
}

/**
 * Actual writer actor, a single instance will be created for writing each block of a file
 */
class TDFSWriterActor(filename: String, fullname: String, filepart: Int, blockSize: Int, sourceNode: String) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Create the file
    val writer = new FileOutputStream(fullname)
    // Keep track of bytes written
    var byteCount: Int = 0
    
    def receive() = {
        case sp: StopPacket => {
            writer.close()
            self ! PoisonPill
        }
        case tcp: TDFSContentPacket => {
            // Write bytes
            if (byteCount + tcp.content.length > blockSize * 1024 * 2014) {
                // Write the part we can still manage
                writer.write(tcp.content, 0, blockSize * 1024 * 1024 - byteCount)
                // Get remainder
                val remainder = tcp.content.slice(blockSize * 1024 * 1024 - byteCount, tcp.content.length)
                
                // Close this block
                byteCount = 0
                writer.close()
                
                // Open a new writer
                val fut = (Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new TDFSWriteRequest(
                            filename,
                            filepart + 1,
                            Some(blockSize),
                            true,
                            None,
                            false,
                            sourceNode
                    )).asInstanceOf[Future[TDFSWriterReply]]
                
                // Send the new writer the remaining content and return writer actorref to the sender
                fut.onSuccess {
                    case twreply: TDFSWriterReply => {
                        twreply.writer ! new TDFSContentPacket(remainder)
                        Cache.getAs[collection.mutable.Map[String, ActorRef]]("tuktu.dfs.WriterMap")
                            .getOrElse(collection.mutable.Map[String, ActorRef]()) += filename -> twreply.writer
                        
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
class TextTDFSWriterActor(filename: String, fullname: String, encoding: String, filepart: Int, blockSize: Int, sourceNode: String) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Create the file
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fullname), encoding))
    // Keep track of bytes written
    var byteCount: Int = 0
    
    def receive() = {
        case sp: StopPacket => {
            // Update cache
            Cache.getAs[collection.mutable.Map[String, ActorRef]]("tuktu.dfs.WriterMap")
                .getOrElse(collection.mutable.Map[String, ActorRef]()) -= filename
            
            println("Closing writer")
            writer.close()
            self ! PoisonPill
        }
        case tcp: TDFSContentPacket => {
            println("Content length: "+ tcp.content.length + " (current: " + byteCount + " -- max: " + (blockSize * 1024 * 1024) + ")")
            // Write bytes
            if (byteCount + tcp.content.length > blockSize * 1024 * 1024) {
                // Write the part we can still manage
                val newContent = tcp.content.slice(0, blockSize * 1024 * 1024)
                writer.write(new String(newContent, encoding))
                println("Writing first part")
                // Get remainder
                val remainder = tcp.content.slice(blockSize * 1024 * 1024 - byteCount, tcp.content.length)
                println("Getting remainder")
                
                // Open a new writer
                val fut = (Akka.system.actorSelection("user/tuktu.dfs.Daemon") ? new TDFSWriteRequest(
                            filename,
                            filepart + 1,
                            Some(blockSize),
                            false,
                            Some(encoding),
                            false,
                            sourceNode
                    )).asInstanceOf[Future[TDFSWriterReply]]
                
                // Send the new writer the remaining content and return writer actorref to the sender
                fut.onSuccess {
                    case twreply: TDFSWriterReply => {
                        twreply.writer ! new TDFSContentPacket(remainder)
                        Cache.getAs[collection.mutable.Map[String, ActorRef]]("tuktu.dfs.WriterMap")
                            .getOrElse(collection.mutable.Map[String, ActorRef]()) += filename -> twreply.writer
                        
                        // Close this block
                        byteCount = 0
                        writer.close()
                        
                        // We can stop now
                        self ! new StopPacket
                    }
                }
                fut.onFailure {
                    case a: Any => {
                        log.error(a, "[TDFS] - Failed to obtain a new writer in a timely manner.")
                        
                        // Close this block
                        byteCount = 0
                        writer.close()
                        
                        // We can stop now
                        self ! new StopPacket
                    }
                }
            }
            else {
                println("Writing entirely: ")
                // Write entirely
                writer.write(new String(tcp.content, encoding))
                byteCount += tcp.content.length
            }
        }
    }
}