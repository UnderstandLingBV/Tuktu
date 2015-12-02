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

case class TDFSWriteRequest(
        filename: String,
        filepart: Int,
        binary: Boolean,
        blockSize: Option[Int],
        nodeSet: Boolean
)
case class TDFSWriterReply(
        writer: ActorRef
)
case class TDFSContentPacket(
        content: Array[Byte]
)

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
        val writer = if (twr.binary)
                Akka.system.actorOf(Props(classOf[TDFSBinaryWriterActor], partname, blockSize),
                    name = "tuktu.dfs.Writer.binary." + partname)
            else
                Akka.system.actorOf(Props(classOf[TDFSTextWriterActor], partname, blockSize),
                    name = "tuktu.dfs.Writer.binary." + partname)
        
        // TODO: Keep track of writers per file name in Cache ?

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
                // Determine the node to write to
                val node = dfsNodes.toList(nodeOffset % dfsNodes.size)
                nodeOffset = (nodeOffset + 1) % dfsNodes.size
                
                // Check if we are the node that needs to write or not
                if (node._1 == thisNode) sender ! new TDFSWriterReply(createWriter(twr))
                else {
                    // Ask the other node for a writer
                    val otherNode = node._2
                    val location = "akka.tcp://application@" + otherNode.host  + ":" + otherNode.akkaPort + "/user/tuktu.dfs.Daemon"
                    
                    // TODO: Create writer and send back
                    val fut = Akka.system.actorSelection(location) ? new TDFSWriteRequest(
                            twr.filename,
                            twr.filepart,
                            twr.binary,
                            twr.blockSize,
                            true
                    )
                }
            }
        }
    }
}

class TDFSBinaryWriterActor(filename: String, filepart: Int, blockSize: Int) extends Actor with ActorLogging {
    // Create the file
    val writer = new FileOutputStream(filename)
    // Keep track of bytes written
    var byteCount: Long = 0
    
    def receive() = {
        case sp: StopPacket => writer.close()
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
                // TODO: Open a new writer and send it the remainder
                
                // TODO: Return writer actorref to the sender
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

class TDFSTextWriterActor(filename: String, blockSize: Int) extends Actor with ActorLogging {
    // Create the file
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), "utf-8"))
    // Keep track of bytes written
    var currentByte: Long = 0
    
    def receive() = {
        case sp: StopPacket => writer.close()
        case tcp: TDFSContentPacket => {
            
        }
    }
}
