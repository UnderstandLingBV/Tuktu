package tuktu.csv.generators

import scala.io.Codec
import akka.actor._
import com.opencsv.CSVReader
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee._
import play.api.libs.json.JsValue
import tuktu.api._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Try, Success, Failure }

/**
 * Reads a CSV file in batches of lines
 */
class BatchedCSVReader(parentActor: ActorRef, fileName: String, encoding: String, hasHeaders: Boolean, givenHeaders: List[String],
                       separator: Char, quote: Char, escape: Char, batchSize: Integer, flattened: Boolean, resultName: String,
                       startLine: Option[Int], endLine: Option[Int], ignoreErrors: Boolean, backOffInterval: Int, backOffAmount: Int) extends Actor with ActorLogging {
    case class ReadLinePacket()

    var batch = collection.mutable.Queue[Map[String, Any]]()
    var headers: Option[List[String]] = _
    var lineOffset: Int = 0
    var reader: CSVReader = _

    def receive = {
        case ip: InitPacket =>
            // Open CSV file for reading
            reader = new CSVReader(tuktu.api.file.genericReader(fileName)(Codec(encoding)), separator, quote, escape)
            // See if we need to fetch headers
            headers = {
                if (hasHeaders) Option(reader.readNext).map { _.toList }
                else if (givenHeaders.nonEmpty) Some(givenHeaders)
                else None
            }
            // Drop first startLine lines
            while (lineOffset < startLine.getOrElse(0) && reader.readNext != null) lineOffset += 1

            // Start processing
            self ! new ReadLinePacket

        case sp: StopPacket =>
            if (batch.nonEmpty) {
                // Send data
                parentActor ! DataPacket(batch.toList)

                // Clear batch
                batch.clear
            }

            // Close CSV reader, kill parent and self
            reader.close
            parentActor ! sp

        case pkt: ReadLinePacket =>
            // Check if we need to backoff
            if (lineOffset != 0 && lineOffset % backOffInterval == 0) Thread.sleep(backOffAmount)

            Try(reader.readNext) match {
                case Failure(e) =>
                    if (ignoreErrors) {
                        play.api.Logger.warn("Error reading content line " + lineOffset + " from file " + fileName, e)
                        self ! pkt
                    } else
                        throw new Exception("Error reading content line " + lineOffset + " from file " + fileName)
                case Success(null) => self ! new StopPacket // EOF, stop processing
                case Success(line) =>
                    // Have we reached endLine yet or not
                    if (endLine.map(endLine => lineOffset <= endLine).getOrElse(true)) {
                        // Add line to our batch
                        batch ++= {
                            headers match {
                                case Some(hdrs) =>
                                    if (hdrs.size != line.size) {
                                        if (ignoreErrors) {
                                            // Ignore, but warn about erroneous line
                                            play.api.Logger.warn("Error reading content line " + lineOffset + " (" + line.mkString(separator.toString) + ") from file " + fileName + ". Expected " + hdrs.size + " columns, found " + line.size)
                                            Nil
                                        } else
                                            throw new NoSuchElementException("Error reading content line " + lineOffset + " (" + line.mkString(separator.toString) + ") from file " + fileName + ". Expected " + hdrs.size + " columns, got " + line.size)
                                    } else
                                        List(flattened match {
                                            case false => Map(resultName -> hdrs.zip(line).toMap)
                                            case true  => hdrs.zip(line).toMap
                                        })
                                case None => List(Map(resultName -> line.toList))
                            }
                        }

                        // Flush if we have to
                        if (batch.size == batchSize) {
                            // Send data
                            parentActor ! DataPacket(batch.toList)

                            // Clear batch
                            batch.clear
                        }

                        // Continue with next line
                        lineOffset += 1
                        self ! pkt
                    } else
                        // Stop reading
                        self ! new StopPacket
            }
    }
}

class CSVGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var csvGenActor: ActorRef = _

    override def _receive = {
        case config: JsValue =>
            // Get filename
            val fileName = (config \ "filename").as[String]
            val hasHeaders = (config \ "has_headers").asOpt[Boolean].getOrElse(false)
            val givenHeaders = (config \ "predef_headers").asOpt[List[String]].getOrElse(List())
            val flattened = (config \ "flattened").asOpt[Boolean].getOrElse(false)
            val separator = (config \ "separator").asOpt[String].flatMap { _.headOption }.getOrElse(';')
            val quote = (config \ "quote").asOpt[String].flatMap { _.headOption }.getOrElse('\"')
            val escape = (config \ "escape").asOpt[String].flatMap { _.headOption }.getOrElse('\\')
            val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
            val startLine = (config \ "start_line").asOpt[Int]
            val endLine = (config \ "end_line").asOpt[Int]
            val batchSize = (config \ "batch_size").asOpt[Int].getOrElse(1000)
            val batched = (config \ "batched").asOpt[Boolean].getOrElse(false)
            val ignoreErrors = (config \ "ignore_error_lines").asOpt[Boolean].getOrElse(false)
            val backOffInterval = (config \ "backoff_interval").asOpt[Int].getOrElse(1000)
            val backOffAmount = (config \ "backoff_amount").asOpt[Int].getOrElse(10)

            if (batched) {
                // Batched uses batching actor
                csvGenActor = Akka.system.actorOf(Props(classOf[BatchedCSVReader], self, fileName, encoding, hasHeaders, givenHeaders,
                    separator, quote, escape, batchSize, flattened, resultName, startLine, endLine, ignoreErrors, backOffInterval, backOffAmount))
                csvGenActor ! new InitPacket
            } else {
                // Make separate enumerators, for each processor
                for ((processor, i) <- processors.zipWithIndex) {
                    // Use Iteratee lib for proper back pressure handling
                    val reader = new CSVReader(tuktu.api.file.genericReader(fileName)(Codec(encoding)), separator, quote, escape)
                    // Headers
                    val headers: Option[List[String]] = {
                        if (hasHeaders) Option(reader.readNext).map { _.toList }
                        else if (givenHeaders.nonEmpty) Some(givenHeaders)
                        else None
                    }

                    val fileStream: Enumerator[Array[String]] = Enumerator.generateM[Array[String]] {
                        Future { Option(reader.readNext) }
                    }.andThen(Enumerator.eof)

                    // Zip lines with index for error messages
                    val zipWithIndex = Enumeratee.scanLeft[Array[String]]((null.asInstanceOf[Array[String]], 0)) {
                        case ((_, index), value) =>
                            (value, index + 1)
                    }
                    // If startLine is positive, drop that many lines
                    val dropEnumeratee = startLine match {
                        case None            => zipWithIndex
                        case Some(startLine) => zipWithIndex compose Enumeratee.drop[(Array[String], Int)](startLine)
                    }
                    // If endLine is defined, take at most endLine - startLine + 1 lines
                    val takeEnumeratee = endLine match {
                        case None          => dropEnumeratee
                        case Some(endLine) => dropEnumeratee compose Enumeratee.take[(Array[String], Int)](endLine - math.max(startLine.getOrElse(0), 0) + 1)
                    }

                    // Zip the lines with the header
                    val zipEnumeratee: Enumeratee[(Array[String], Int), Map[String, Any]] = Enumeratee.mapM[(Array[String], Int)] {
                        case (line: Array[String], index: Int) =>
                            Future {
                                headers match {
                                    case Some(hdrs) =>
                                        if (hdrs.size != line.size) {
                                            if (ignoreErrors) {
                                                // Ignore, but warn about erroneous line
                                                play.api.Logger.warn("Error reading content line " + index + " (" + line.mkString(separator.toString) + ") from file " + fileName + ". Expected " + hdrs.size + " columns, found " + line.size)
                                                Map.empty[String, Any]
                                            } else
                                                throw new NoSuchElementException("Error reading content line " + index + " (" + line.mkString(separator.toString) + ") from file " + fileName + ". Expected " + hdrs.size + " columns, got " + line.size)
                                        } else
                                            flattened match {
                                                case false => Map(resultName -> hdrs.zip(line).toMap)
                                                case true  => hdrs.zip(line).toMap
                                            }
                                    case None => Map(resultName -> line.toList)
                                }
                            }
                    }

                    // onEOF close the reader and send StopPacket for last proc
                    val onEOF = Enumeratee.onEOF[Map[String, Any]] { () =>
                        reader.close
                        if (i == processors.size - 1)
                            self ! new StopPacket
                    }

                    // Filter erroneous lines
                    val nonEmpty = Enumeratee.filter[Map[String, Any]] { _.nonEmpty }

                    // Stream it all together
                    fileStream |>> (takeEnumeratee compose zipEnumeratee compose nonEmpty compose onEOF compose Enumeratee.mapM(data => Future {
                        DataPacket(List(data))
                    }) compose processor) &>> sinkIteratee
                }
            }
        case sp: StopPacket =>
            Option(csvGenActor) collect {
                case actor =>
                    actor ! PoisonPill
                    channel.eofAndEnd
            }
            cleanup(false)
        case data: DataPacket => channel.push(data)
    }
}