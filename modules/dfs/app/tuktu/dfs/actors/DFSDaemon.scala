package tuktu.dfs.actors

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Path
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
import java.nio.file.Paths
import java.nio.file.Files

case class TDFSWriteInitiateRequest(
        filename: String,
        blockSize: Option[Int],
        binary: Boolean,
        encoding: Option[String]
)
case class TDFSReadInitiateRequest(
        filename: String,
        binary: Boolean,
        encoding: Option[String],
        chunkSize: Option[Int]
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
        encoding: Option[String],
        chunkSize: Option[Int]
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
case class TDFSOverviewPacket(
        filename: String,
        isFolder: Boolean
)
case class TDFSOverviewReply(
        files: Map[String, List[Int]] // File name to list of part indices
)
case class TDFSDeleteRequest(
        file: String
)

/**
 * Daemon actor that handles write and read requests
 */
class TDFSDaemon extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    def receive() = {
        case tir: TDFSWriteInitiateRequest => {
            val fName = Paths.get(tir.filename).normalize.toString
            // Set up the writer actor that will take care of the rest and return the ref to sender
            sender ! Akka.system.actorOf(Props(classOf[WriterDaemon], tir),
                    name = "tuktu.dfs.WriterDaemon." + fName.replaceAll("/|\\\\", "_") + "_" + System.currentTimeMillis)
        }
        case tdr: TDFSDeleteRequest => {
            val name = Paths.get(tdr.file).normalize.toString
            // Remove the file from our mapping
            val fileTable = Cache.getAs[collection.mutable.Map[String, collection.mutable.ArrayBuffer[Int]]]("tuktu.dfs.NodeFileTable")
                .getOrElse(collection.mutable.Map.empty[String, collection.mutable.ArrayBuffer[Int]])
            val parts = fileTable.get(name)
            fileTable -= name
            
            // Inform all other daemons
            val clusterNodes = Cache.getOrElse[scala.collection.mutable.Map[String, ClusterNode]]("clusterNodes")(scala.collection.mutable.Map())
            val futs = clusterNodes.foreach(node => {
                if (node._1 != Cache.getAs[String]("homeAddress").getOrElse("127.0.0.1"))
                    Akka.system.actorSelection("akka.tcp://application@" + node._2.host + ":" + node._2.akkaPort + "/user/tuktu.dfs.Daemon") ! tdr
            })
            
            // Remove anything we have on disk
            parts match {
                case Some(f) => try {
                    // Delete the file
                    val prefix = Cache.getAs[String]("tuktu.dfs.prefix").getOrElse(Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs"))
                    f.foreach {part =>
                        Files.delete(Paths.get(prefix + "/" + name + ".part-" + part))
                    }
                } catch {
                    case e: Exception => {}
                }
                case None => {}
            }
            
            sender ! "ok"
        }
        case top: TDFSOverviewPacket => {
            // Check if we have files inside the request folder or parts for the requested filename
            val fileTable = Cache.getAs[collection.mutable.Map[String, collection.mutable.ArrayBuffer[Int]]]("tuktu.dfs.NodeFileTable")
                .getOrElse(collection.mutable.Map.empty[String, collection.mutable.ArrayBuffer[Int]])
                
            val reply = {
                val requestPath = Paths.get(top.filename).normalize
                
                // Our map contains files and folders, we need to potentially scan all of them because only files are stored
                val potentialFiles: Map[String, List[Int]] = if (top.isFolder) {
                    // We are matching for a folder, we need to find all subfolders and files
                    val requestList = if (top.filename == "") List.empty[String] else util.pathBuilderHelper(requestPath.iterator)
                    
                    // Fetch the right elements
                    (for {
                        fileRecord <- fileTable
                        
                        // Use paths for comparison
                        entryPath = Paths.get(fileRecord._1).normalize
                        
                        // Now check if our folders match or not
                        if (top.filename == "" || entryPath.startsWith(requestPath))
                    } yield {
                        // Chop the file path up into the pieces we need
                        val entryList = util.pathBuilderHelper(entryPath.iterator).take(requestList.size + 1)
                        
                        // Check if this is a folder or a file
                        if (fileTable.contains(Paths.get(entryList.mkString("/")).toString)) {
                            // File, just return the pieces
                            entryPath.toString -> fileTable(entryPath.toString).toList
                        }
                        else {
                            // Folder, return the name of the subfolder only
                            entryList.drop(requestList.size).head.toString -> List.empty[Int]
                        }
                    }).toMap
                }
                else {
                    // Simply file match, get the parts we own
                    if (fileTable.contains(requestPath.toString)) {
                        // File, just return the pieces
                        Map(requestPath.toString -> fileTable(requestPath.toString).toList)
                    } else Map.empty[String, List[Int]]
                }
                
                // Make the reply
                new TDFSOverviewReply(potentialFiles)
            }
            
            // Send back
            sender ! reply
        }
        case twr: TDFSWriteRequest => {
            // We are asked to make a writer for a partfile, keep track of it
            val fileTable = Cache.getAs[collection.mutable.Map[String, collection.mutable.ArrayBuffer[Int]]]("tuktu.dfs.NodeFileTable")
                .getOrElse(collection.mutable.Map.empty[String, collection.mutable.ArrayBuffer[Int]])
            // Use paths for consistency
            val fName = Paths.get(twr.filename).normalize.toString
            if (fileTable.contains(fName))
                fileTable(fName) += twr.part
            else
                 fileTable += fName -> collection.mutable.ArrayBuffer[Int](twr.part)
            
            // Text or binary?
            sender ! {
                if (twr.binary)
                    Akka.system.actorOf(Props(classOf[BinaryTDFSWriterActor], twr),
                            name = "tuktu.dfs.Writer.binary." + fName.replaceAll("/|\\\\", "_") + ".part" + twr.part + "_" + System.currentTimeMillis)
                else
                    Akka.system.actorOf(Props(classOf[TextTDFSWriterActor], twr),
                            name = "tuktu.dfs.Writer.text." + fName.replaceAll("/|\\\\", "_") + ".part" + twr.part + "_" + System.currentTimeMillis)
            }
        }
        case trr: TDFSReadInitiateRequest => {
            // Create the reader daemon and send back the ref
            val fName = Paths.get(trr.filename).normalize.toString
            Akka.system.actorOf(Props(classOf[ReaderDaemon], trr, sender),
                    name = "tuktu.dfs.ReaderDaemon." + fName.replaceAll("/|\\\\", "_") + "_" + System.currentTimeMillis)
        }
        case tcr: TDFSReadRequest => {
            // Check if we have the request part, or need to cache
            val fileTable = Cache.getAs[collection.mutable.Map[String, collection.mutable.ArrayBuffer[Int]]]("tuktu.dfs.NodeFileTable")
                .getOrElse(collection.mutable.Map.empty[String, collection.mutable.ArrayBuffer[Int]])
            val fName = Paths.get(tcr.filename).normalize.toString
            if (fileTable.contains(fName)) {
                // Check part and if we need to do anything
                if (fileTable(fName).contains(tcr.part)) {
                    // Set up the actual reader
                    if (tcr.binary)
                        Akka.system.actorOf(Props(classOf[BinaryTDFSReaderActor], tcr, sender),
                                name = "tuktu.dfs.Reader.binary." + fName.replaceAll("/|\\\\", "_") + ".part" + tcr.part + "_" + System.currentTimeMillis)
                    else
                        Akka.system.actorOf(Props(classOf[TextTDFSReaderActor], tcr, sender),
                                name = "tuktu.dfs.Reader.text." + fName.replaceAll("/|\\\\", "_") + ".part" + tcr.part + "_" + System.currentTimeMillis)
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
        case _ => Cache.getAs[Int]("tuktu.dfs.blocksize").getOrElse(64)
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
    val prefix = Cache.getAs[String]("tuktu.dfs.prefix").getOrElse(Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs"))
    
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
            val fName = Paths.get(twr.filename).normalize.toString
            Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
                .getOrElse(collection.mutable.Map.empty[String, Int]) += fName -> twr.part
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
    val prefix = Cache.getAs[String]("tuktu.dfs.prefix").getOrElse(Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs"))
    
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
            val fName = Paths.get(twr.filename).normalize.toString
            Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
                .getOrElse(collection.mutable.Map.empty[String, Int]) += fName -> twr.part
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
                    trr.filename, currentCount, trr.binary, trr.encoding, trr.chunkSize
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
    val prefix = Cache.getAs[String]("tuktu.dfs.prefix").getOrElse(Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs"))

    // Set up the reader
    val reader = new BufferedReader(new InputStreamReader(new FileInputStream(
            prefix + "/" + trr.filename + ".part-" + trr.part), trr.encoding.getOrElse("utf-8")))
    // Start reading
    val firstLine = reader.readLine
    if (firstLine == null)
        self ! new LineReaderContent(null, null)
    else
        self ! new LineReaderContent(firstLine, reader.readLine)
    
    val newLineSymbol = String.format("%n").intern
    
    def receive() = {
        case lrc: LineReaderContent => {
            if (lrc.previous == null) {
                // Send local EOF, but maybe global too?
                val globalEof = {
                    val fName = Paths.get(trr.filename).normalize.toString
                    val eofs = Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
                        .getOrElse(collection.mutable.Map.empty[String, Int])
                    if (eofs.contains(fName)) eofs(fName) == trr.part
                    else false
                }
                requester ! new TDFSReadContentEOF(globalEof)
                reader.close
                self ! PoisonPill
            }
            else {
                val nextLine = reader.readLine
                // Send it to the requester
                requester ! new TDFSTextReadContentPacket(lrc.previous + newLineSymbol, lrc.current == null)
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
    val prefix = Cache.getAs[String]("tuktu.dfs.prefix").getOrElse(Play.current.configuration.getString("tuktu.dfs.prefix").getOrElse("dfs"))
    
    // Set up the reader
    val reader = new FileInputStream(new File(trr.filename + ".part-" + trr.part))
    // Start reading
    self ! doRead
    
    def doRead() = {
        val content = new Array[Byte](trr.chunkSize.getOrElse(8 * 1024))
        val res = reader.read(content)
        new TDFSBinaryReadContentPacket(content, res)
    }
    
    def receive() = {
        case tbrcp: TDFSBinaryReadContentPacket => {
            // Check if it was successful
            if (tbrcp.result == -1) {
                // We are done, send local EOF, but maybe global too?
                val globalEof = {
                    val fName = Paths.get(trr.filename).normalize.toString
                    val eofs = Cache.getAs[collection.mutable.Map[String, Int]]("tuktu.dfs.NodeFileTable.eofs")
                        .getOrElse(collection.mutable.Map.empty[String, Int])
                    if (eofs.contains(fName)) eofs(fName) == trr.part
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