package tuktu.generators

import java.io._
import java.nio.file._
import akka.actor._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api._
import scala.io.Codec
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import scala.xml.XML
import play.api.libs.json.JsObject

/**
 * Actor that reads file immutably and non-blocking
 */
class LineReader(parentActor: ActorRef, resultName: String, fileName: String, encoding: String, batchSize: Int,
        startLine: Int, endLine: Option[Int], backOffInterval: Int, backOffAmount: Int) extends Actor with ActorLogging {
    case class ReadLinePacket()

    var batch = collection.mutable.Queue.empty[Map[String, Any]]
    var reader: BufferedReader = _
    var lineOffset = 0

    def receive() = {
        case ip: InitPacket => {
            // Open the file in reader
            reader = file.genericReader(fileName)(Codec(encoding))

            // Drop first startLine lines
            while (lineOffset < startLine && reader.readLine != null) lineOffset += 1

            // Start processing
            self ! new ReadLinePacket
        }
        case sp: StopPacket => {
            // Close reader, send back the rest, and stop
            reader.close
            if (batch.nonEmpty) {
                parentActor ! batch.toList
                batch.clear
            }
            parentActor ! sp
        }
        case pkt: ReadLinePacket => reader.readLine match {
            case null => self ! new StopPacket // EOF, stop processing
            case line: String => {
                // Check if we need to backoff
                if (lineOffset != 0 && lineOffset % backOffInterval == 0) Thread.sleep(backOffAmount)
                
                // Have we reached endLine yet or not
                if (endLine.map(endLine => lineOffset <= endLine).getOrElse(true)) {
                    // Buffer line
                    batch += Map(resultName -> line)
                    // Send only if required
                    if (batch.size == batchSize) {
                        parentActor ! batch.toList
                        batch.clear
                    }

                    // Continue with next line
                    lineOffset += 1
                    self ! pkt
                } else {
                    // We are past the endLine, stop
                    self ! new StopPacket
                }
            }
        }
    }
}

/**
 * Streams a file line by line
 */
class LineGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var lineGenActor: ActorRef = _
    
    override def receive() = {
        case dpp: DecreasePressurePacket => decBP
        case bpp: BackPressurePacket => backoff
        case config: JsValue => {
            // Get filename
            val fileName = (config \ "filename").as[String]
            val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
            val startLine = (config \ "start_line").asOpt[Int].getOrElse(0)
            val endLine = (config \ "end_line").asOpt[Int]
            val batchSize = (config \ "batch_size").asOpt[Int].getOrElse(1000)
            val batched = (config \ "batched").asOpt[Boolean].getOrElse(false)
            val backOffInterval = (config \ "backoff_interval").asOpt[Int].getOrElse(1000)
            val backOffAmount = (config \ "backoff_amount").asOpt[Int].getOrElse(10)

            if (batched) {
                // Batching is done using the actor
                lineGenActor = Akka.system.actorOf(Props(classOf[LineReader], self, resultName, fileName, encoding, batchSize, startLine, endLine, backOffInterval, backOffAmount))
                lineGenActor ! new InitPacket
            } else {
                // Make separate enumerators, for each processor
                processors.foreach(processor => {
                    // Use Iteratee lib for proper back pressure handling
                    val bufferedReader = file.genericReader(fileName)(Codec(encoding))
                
                    val fileStream: Enumerator[String] = Enumerator.generateM[String] {
                        Future { Option(bufferedReader.readLine) }
                    }.andThen(Enumerator.eof)
    
                    // onEOF close the reader and send StopPacket
                    val onEOF = Enumeratee.onEOF[String](() => {
                        bufferedReader.close
                        self ! new StopPacket
                    })
                    // If endLine is defined, take at most endLine - startLine + 1 lines
                    val endEnumeratee = endLine match {
                        case None          => onEOF
                        case Some(endLine) => Enumeratee.take[String](endLine - math.max(startLine, 0) + 1) compose onEOF
                    }
                    // If startLine is positive, drop that many lines
                    val startEnumeratee =
                        if (startLine <= 0) endEnumeratee
                        else Enumeratee.drop[String](startLine) compose endEnumeratee
                        
                    fileStream |>> (startEnumeratee compose Enumeratee.mapM(line => Future {
                        DataPacket(List(Map(resultName -> line)))
                    }) compose processor) &>> sinkIteratee
                })
            }
        }
        case sp: StopPacket => {
            Option(lineGenActor) collect { case actor => actor ! PoisonPill }
            cleanup(false)
        }
        case ip: InitPacket               => setup
        case data: List[Map[String, Any]] => channel.push(new DataPacket(data))
    }
}

case class PathsPacket(paths: List[Path], iterator: java.util.Iterator[Path])

/**
 * Actor that reads files and directories non-blocking
 */
class FileDirectoryReader(parentActor: ActorRef, pathMatcher: PathMatcher, recursive: Boolean) extends Actor with ActorLogging {
    def receive() = {
        case sp: StopPacket => {
            parentActor ! new StopPacket
            self ! PoisonPill
        }
        case pp: PathsPacket => {
            // Check if iterator has elements
            if (pp.iterator != null && pp.iterator.hasNext) {
                val path = pp.iterator.next
                if (Files.isDirectory(path)) {
                    // If path is a directory and recursive is true, add it to the paths to be processed, otherwise ignore it
                    if (recursive)
                        self ! new PathsPacket(path :: pp.paths, pp.iterator)
                    else
                        self ! pp
                } else {
                    // If we have a regular file that matches our pathMatcher, send it to parentActor
                    if (Files.isRegularFile(path) && pathMatcher.matches(path))
                        parentActor ! path
                    self ! pp
                }
            } else {
                // Iterator is empty
                pp.paths match {
                    case path :: paths => {
                        if (Files.isDirectory(path)) {
                            // Instantiate iterator with new directory
                            self ! new PathsPacket(paths, Files.newDirectoryStream(path).iterator)
                        } else {
                            // Send regular file to parentActor if it matches our pathMatcher
                            if (Files.isRegularFile(path) && pathMatcher.matches(path))
                                parentActor ! path
                            self ! new PathsPacket(paths, pp.iterator)
                        }
                    }
                    case Nil => {
                        // Iterator is empty and no more paths to process: Stop
                        self ! new StopPacket
                    }
                }
            }
        }
    }
}

/**
 * Streams files of directories file by file (as java.nio.file.Path)
 */
class FilesGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get config variables
            val filesAndDirs = (config \ "filesAndDirs").asOpt[List[String]].getOrElse(Nil)
            val recursive = (config \ "recursive").asOpt[Boolean].getOrElse(false)
            val pathMatcher = (config \ "pathMatcher").asOpt[String].getOrElse("glob:**")

            val defaultFS = FileSystems.getDefault

            // Create non-blocking actor and start it
            val fileDirActor = Akka.system.actorOf(Props(classOf[FileDirectoryReader], self, defaultFS.getPathMatcher(pathMatcher), recursive))
            fileDirActor ! new PathsPacket(filesAndDirs.map(defaultFS.getPath(_)), null)
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
        case path: Path     => channel.push(new DataPacket(List(Map(resultName -> path))))
    }
}

/**
 * Reads XML in as bulk and streams an XPath selection forward
 */
class XmlGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get the file name
            val filename = (config \ "file_name").as[String]
            val query = (config \ "query").asOpt[List[JsObject]].getOrElse(List())
            val asText = (config \ "as_text").asOpt[Boolean].getOrElse(false)
            val trim = (config \ "trim").asOpt[Boolean].getOrElse(false)
            
            // Read in XML
            val xml = XML.loadFile(filename)
            
            // Get the elements we are after
            val nodes = utils.xmlQueryParser(xml.head, query)
            if (asText) nodes.foreach(el => channel.push(new DataPacket(List(Map(resultName -> {
                    if (trim) el.text.trim
                    else el.text
                })))))
            else nodes.foreach(el => channel.push(new DataPacket(List(Map(resultName -> el)))))
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}