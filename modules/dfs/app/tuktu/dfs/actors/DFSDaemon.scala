package tuktu.dfs.actors

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import tuktu.api.ClusterNode
import tuktu.api.StopPacket
import tuktu.dfs.util.util
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import scala.io.Codec
import java.io.BufferedInputStream
import java.io.DataInputStream

case class TDFSWriteInitiateRequest(
        filename: String,
        blockSize: Option[Int],
        binary: Boolean,
        encoding: Option[String]
)
case class TDFSReadInitiateRequest(
        filename: String,
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
case class TDFSReadRequest(
        filename: String,
        part: Int,
        binary: Boolean,
        encoding: Option[String]
)
case class TDFSContentPacket(
        content: Array[Byte]
)
case class TDFSTextReadContentPacket(
        content: String,
        isEof: Boolean
)
case class TDFSBinaryReadContentPacket(
        content: Array[Byte],
        result: Int
)
case class TDFSContentEOF()
case class TDFSReadContentEOF(
        global: Boolean
)

/**
 * Daemon actor that handles write and read requests
 */
class TDFSDaemon extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    def receive() = {
        case tir: TDFSWriteInitiateRequest => {
            // Set up the writer actor that will take care of the rest and return the ref to sender
            sender ! Akka.system.actorOf(Props(classOf[WriterDaemon], tir),
                    name = "tuktu.dfs.WriterDaemon." + tir.filename + "_" + System.currentTimeMillis)
        }
        case twr: TDFSWriteRequest => {
            // We are asked to make a writer for a partfile, keep track of it
            val fileTable = Cache.getAs[collection.mutable.Map[String, collection.mutable.ArrayBuffer[Int]]]("tuktu.dfs.NodeFileTable")
                .getOrElse(collection.mutable.Map.empty[String, collection.mutable.ArrayBuffer[Int]])
            if (fileTable.contains(twr.filename))
                fileTable(twr.filename) += twr.part
            else
                 fileTable += twr.filename -> collection.mutable.ArrayBuffer[Int](twr.part)
            
            // Text or binary?
            sender ! {
                if (twr.binary)
                    Akka.system.actorOf(Props(classOf[BinaryTDFSWriterActor], twr),
                            name = "tuktu.dfs.Writer.binary." + twr.filename + ".part" + twr.part + "_" + System.currentTimeMillis)
                else
                    Akka.system.actorOf(Props(classOf[TextTDFSWriterActor], twr),
                            name = "tuktu.dfs.Writer.text." + twr.filename + ".part" + twr.part + "_" + System.currentTimeMillis)
            }
        }
        case trr: TDFSReadInitiateRequest => {
            // Create the reader daemon and send back the ref
            Akka.system.actorOf(Props(classOf[ReaderDaemon], trr, sender),
                    name = "tuktu.dfs.ReaderDaemon." + trr.filename + "_" + System.currentTimeMillis)
        }
        case tcr: TDFSReadRequest => {
            // Check if we have the request part, or need to cache
            val fileTable = Cache.getAs[collection.mutable.Map[String, collection.mutable.ArrayBuffer[Int]]]("tuktu.dfs.NodeFileTable")
                .getOrElse(collection.mutable.Map.empty[String, collection.mutable.ArrayBuffer[Int]])
            if (fileTable.contains(tcr.filename)) {
                // Check part and if we need to do anything
                if (fileTable(tcr.filename).contains(tcr.part)) {
                    // Set up the actual reader
                    if (tcr.binary)
                        Akka.system.actorOf(Props(classOf[BinaryTDFSReaderActor], tcr, sender),
                                name = "tuktu.dfs.Reader.binary." + tcr.filename + ".part" + tcr.part + "_" + System.currentTimeMillis)
                    else
                        Akka.system.actorOf(Props(classOf[TextTDFSReaderActor], tcr, sender),
                                name = "tuktu.dfs.Reader.text." + tcr.filename + ".part" + tcr.part + "_" + System.currentTimeMillis)
                }
            }
        }
    }
}

/**
 * Daemon that is always alive on the node the TDFS file is written from, to make sure there is always an endpoint. This daemon
 * acts as a proxy to the actual (remote) writer actors
 */
class WriterDaemon(tir: TDFSWriteInitiateRequest) extends Actor with ActorLogging {
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
        val node = clusterNodes.toList(nodeOffset % clusterNodes.size)._2
        nodeOffset = (nodeOffset + 1) % clusterNodes.size
        
        // Ask the TDFS Daemon for a writer
        val location = if (node.host == homeAddress) "user/tuktu.dfs.Daemon"
            else
                "akka.tcp://application@" + node.host  + ":" + node.akkaPort + "/user/tuktu.dfs.Daemon"
        
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
            // Check if we can still add to the current block
            if (byteCount + tcp.content.length > blockSize * 1024 * 1024) {
                // We are going to be full after this block, make sure we forward the part that can still be written
                writer ! new TDFSContentPacket(tcp.content.slice(0, blockSize * 1024 * 1024 - byteCount))
                val remainder = new TDFSContentPacket(tcp.content.slice(blockSize * 1024 * 1024 - byteCount, tcp.content.length))
                
                // Toss away the old writer
                writer ! new StopPacket
                
                // Create the new writer
                byteCount = 0
                currentPart = currentPart + 1
                createWriter
                
                // Send remainder
                writer ! remainder
            } else {
                // Just write the entire thing
                writer ! tcp
                byteCount += tcp.content.length
            }
        }
        case sp: StopPacket => {
            // Current part is the last part, keep track of that
            writer ! new TDFSContentEOF()
            
            // Clean up the writer
            writer ! sp
            
            // Persist the new file tables
            val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
            clusterNodes.foreach(clusterNode => {
                val location = if (clusterNode._2.host == homeAddress) "user/tuktu.dfs.Daemon.persist"
                    else
                        "akka.tcp://application@" + clusterNode._2.host  + ":" + clusterNode._2.akkaPort + "/user/tuktu.dfs.Daemon.persist"
                Akka.system.actorSelection(location) ! new PersistRequest
            })
            
            // Stop
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
        case tce: TDFSContentEOF => {
            // Current part was the last part, keep track of that
            Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
                .getOrElse(collection.mutable.Map.empty[String, Int]) += twr.filename -> twr.part
        }
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
        case tce: TDFSContentEOF => {
            // Current part was the last part, keep track of that
            Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
                .getOrElse(collection.mutable.Map.empty[String, Int]) += twr.filename -> twr.part
        }
        case sp: StopPacket => {
            writer.close()
            self ! PoisonPill
        }
        case tcp: TDFSContentPacket =>
            // Write out
            writer.write(new String(tcp.content, encoding))
    }
}

/**
 * This daemon takes care of reading files from TDFS
 */
class ReaderDaemon(trr: TDFSReadInitiateRequest, requester: ActorRef) extends Actor with ActorLogging {
    val homeAddress = Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1")
    
    // Keep track of where we are 
    var currentCount = 0
    // For text reading, we might get impartial lines, keep track of previous packet
    var lastContent: String = null
    
    def signalReader() = {
        // Advertise the reading of part 0
        val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
        clusterNodes.foreach(clusterNode => {
            val location = if (clusterNode._2.host == homeAddress) "user/tuktu.dfs.Daemon"
                else
                    "akka.tcp://application@" + clusterNode._2.host  + ":" + clusterNode._2.akkaPort + "/user/tuktu.dfs.Daemon"
            Akka.system.actorSelection(location) ! new TDFSReadRequest(
                    trr.filename, currentCount, trr.binary, trr.encoding
            )
        })
    }
    
    signalReader
    
    def receive() = {
        case trce: TDFSReadContentEOF => {
            // Check if we need a new reader, or if we need to stop as a whole
            if (trce.global) {
                // Since we are fully done, we still need to send the accumulated content, if present
                if (lastContent != null)
                    requester ! new TDFSContentPacket(lastContent.getBytes)
                
                // Completed, we are done
                requester ! new StopPacket
                self ! PoisonPill
            }
            else {
                // We need a new reader, request one and rotate
                currentCount += 1
                signalReader
            }
        }
        case tcp: TDFSContentPacket => requester ! tcp
        case tcp: TDFSTextReadContentPacket => {
            // Check if this was the last line
            if (tcp.isEof)
                // Update last content seen
                lastContent = (
                        if (lastContent == null) "" else lastContent
                ) + tcp.content
            else {
                // Check if we need to append our last content to make a full line
                val content = if (lastContent != null)
                    lastContent ++ tcp.content
                else
                    tcp.content
                requester ! new TDFSContentPacket(content.getBytes)
                lastContent = null
            }
        }
    }
}

/**
 * Reads a text file from TDFS line by line
 */
class TextTDFSReaderActor(trr: TDFSReadRequest, requester: ActorRef) extends Actor with ActorLogging {
    case class LineReaderContent(previous: String, current: String)    
    val prefix = Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs")

    // Set up the reader
    val reader = new BufferedReader(new InputStreamReader(new FileInputStream(
            prefix + "/" + trr.filename + ".part-" + trr.part), trr.encoding.getOrElse("utf-8")))
    // Start reading
    val firstLine = reader.readLine
    if (firstLine == null)
        self ! new LineReaderContent(null, null)
    else
        self ! new LineReaderContent(firstLine, reader.readLine)
    
    def receive() = {
        case lrc: LineReaderContent => {
            if (lrc.previous == null) {
                // Send local EOF, but maybe global too?
                val globalEof = {
                    val eofs = Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
                        .getOrElse(collection.mutable.Map.empty[String, Int])
                    if (eofs.contains(trr.filename)) eofs(trr.filename) == trr.part
                    else false
                }
                requester ! new TDFSReadContentEOF(globalEof)
                reader.close
                self ! PoisonPill
            }
            else {
                val nextLine = reader.readLine
                // Send it to the requester
                requester ! new TDFSTextReadContentPacket(lrc.previous, lrc.current == null)
                // Continue with next line
                self ! new LineReaderContent(lrc.current, nextLine)
            }
        }
    }
}

/**
 * Reads a binary file from TDFS in a buffered manner
 */
class BinaryTDFSReaderActor(trr: TDFSReadRequest, requester: ActorRef) extends Actor with ActorLogging {
    val prefix = Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs")
    
    // Set up the reader
    val reader = new FileInputStream(new File(trr.filename + ".part-" + trr.part))
    // Start reading
    self ! doRead
    
    def doRead() = {
        val content = new Array[Byte](8 * 1024)
        val res = reader.read(content)
        new TDFSBinaryReadContentPacket(content, res)
    }
    
    def receive() = {
        case tbrcp: TDFSBinaryReadContentPacket => {
            // Check if it was successful
            if (tbrcp.result == -1) {
                // We are done, send local EOF, but maybe global too?
                val globalEof = {
                    val eofs = Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
                        .getOrElse(collection.mutable.Map.empty[String, Int])
                    if (eofs.contains(trr.filename)) eofs(trr.filename) == trr.part
                    else false
                }
                requester ! new TDFSReadContentEOF(globalEof)
                reader.close
                self ! PoisonPill
            }
            else {
                // Send it to the requester
                requester ! new TDFSContentPacket(tbrcp.content)
                // Continue with next line
                self ! doRead
            }
        }
    }
}