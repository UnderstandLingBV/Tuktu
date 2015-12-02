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

case class TDFSWriteRequest(
        filename: String,
        filepart: Int,
        binary: Boolean,
        blockSize: Option[Int],
        returnWriter: Boolean
)
case class TDFSWriterReply(
        writer: ActorRef
)
case class TDFSContentPacket(
        content: Array[Byte]
)

class TDFSDaemon extends Actor with ActorLogging {
    def receive() = {
        case twr: TDFSWriteRequest => {
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
            if (twr.returnWriter) sender ! new TDFSWriterReply(writer)
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
