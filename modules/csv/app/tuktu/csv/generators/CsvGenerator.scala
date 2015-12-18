package tuktu.csv.generators

import scala.io.Codec
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import au.com.bytecode.opencsv.CSVReader
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee

case class CSVReadPacket(
        reader: CSVReader
)
case class CSVStopPacket(
        reader: CSVReader
)

/**
 * Reads a CSV file in batches of lines
 */
class BatchedCsvReader(parentActor: ActorRef, fileName: String, encoding: String, hasHeaders: Boolean, givenHeaders: List[String],
        separator: Char, quote: Char, escape: Char, batchSize: Integer, flattened: Boolean, resultName: String,
        startLine: Option[Int], endLine: Option[Int]) extends Actor with ActorLogging {
    var batch = collection.mutable.Queue[Map[String, Any]]()
    var headers: Option[List[String]] = None
    var lineOffset = 0
    
    def receive() = {
        case ip: InitPacket => {
            // Open CSV file for reading
            val reader = new CSVReader(tuktu.api.file.genericReader(fileName)(Codec(encoding)), separator, quote, escape)
            // See if we need to fetch headers
            headers = {
                if (hasHeaders) Some(reader.readNext.toList)
                else {
                    if (givenHeaders != List()) Some(givenHeaders)
                    else None
                }
            }
            // Start processing
            self ! new CSVReadPacket(reader)
        }
        case sp: CSVStopPacket => {
            // Send last batch
            val data = (for (item <- batch) yield {
                headers match {
                    case Some(hdrs) => flattened match {
                        case false => Map(resultName -> hdrs.zip(item).toMap)
                        case true => hdrs.zip(item).toMap
                    }
                    case None => Map(resultName -> item)
                }
            }).toList
            
            // Send data
            sender ! new DataPacket(data)
            
            // Clear batch
            batch.clear
                    
            // Close CSV reader, kill parent and self
            sp.reader.close
            parentActor ! new StopPacket
            self ! PoisonPill
        }
        case pkt: CSVReadPacket => pkt.reader.readNext match {
            case null => self ! new CSVStopPacket(pkt.reader) // EOF, stop processing
            case line: Array[String] => {
                // Check if we need to send
                val toSend = {
                    (startLine match {
                        case Some(sl) => lineOffset >= sl
                        case None => true
                    }) && (endLine match {
                        case Some(el) => lineOffset <= el
                        case None => true
                    })
                }
                
                if (toSend) {
                    // Add this to our batch
                    batch += {
                        headers match {
                            case Some(hdrs) => flattened match {
                                case false => Map(resultName -> hdrs.zip(line).toMap)
                                case true => hdrs.zip(line).toMap
                            }
                            case None => Map(resultName -> line.toList)
                        }
                    }
                    
                    // Flush if we have to
                    if (batch.size == batchSize) {
                        // Send data
                        parentActor ! batch.toList
                        
                        // Clear batch
                        batch.clear
                    }
                    
                    // Continue with next line
                    self ! pkt
                }
                else
                    // Stop reading
                    self ! new CSVStopPacket(pkt.reader)
            }
        }
    }
}

class CSVGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var csvGenActor: ActorRef = _
    var headers: Option[List[String]] = null
    
    override def receive() = {
        case config: JsValue => {
            // Get filename
            val fileName = (config \ "filename").as[String]
            val hasHeaders = (config \ "has_headers").asOpt[Boolean].getOrElse(false)
            val headersGiven = (config \ "predef_headers").asOpt[List[String]].getOrElse(List())
            val flattened = (config \ "flattened").asOpt[Boolean].getOrElse(false)
            val separator = (config \ "separator").asOpt[String].getOrElse(";").head
            val quote = (config \ "quote").asOpt[String].getOrElse("\"").head
            val escape = (config \ "escape").asOpt[String].getOrElse("\\").head
            val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
            val startLine = (config \ "start_line").asOpt[Int]
            val endLine = (config \ "end_line").asOpt[Int]
            val batchSize = (config \ "batch_size").asOpt[Int].getOrElse(1000)
            val batched = (config \ "batched").asOpt[Boolean].getOrElse(false)
            
            if (batched) {
                // Batched uses batching actor
                csvGenActor = Akka.system.actorOf(Props(classOf[BatchedCsvReader], self, fileName, encoding, hasHeaders, headersGiven,
                        separator, quote, escape, batchSize, flattened, resultName, startLine, endLine))
                csvGenActor ! new InitPacket()
            }
            else {
                // Use Iteratee lib for proper back pressure handling
                lazy val reader =  new CSVReader(tuktu.api.file.genericReader(fileName)(Codec(encoding)), separator, quote, escape)
                
                // Set the headers
                val headers = { 
                  if (hasHeaders) { 
                    Some(reader.readNext.toList) 
                  }
                  else {
                    if (headersGiven != List()) Some(headersGiven)
                    else None                  
                  }
                }
                     
                // Setup a fileStream to iterate over
                val fileStream : Enumerator[Map[String, Any]] = Enumerator.generateM[Map[String, Any]] {
                    Future {
                        val line: Array[String] = reader.readNext
                        
                        // Turn the array of unnamed columns into a named map, if required
                        Option(headers match {
                            case Some(hdrs) => flattened match {
                                case false => Map(resultName -> hdrs.zip(line).toMap)
                                case true => hdrs.zip(line).toMap
                            }
                            case None => Map(resultName -> line.toList)
                        })
                    }
                }
                
                // Stream the whole thing together now
                fileStream |>> ({
                    val enum: Enumeratee[Map[String, Any], Map[String, Any]] =
                        if (hasHeaders) (Enumeratee.drop(1) compose Enumeratee.onEOF(() => reader.close))
                        else Enumeratee.onEOF(() => reader.close)
                    enum
                }) &>> Iteratee.foreach[Map[String, Any]](data => channel.push(new DataPacket(List(data))))
            }
        }
        case sp: StopPacket => {
            csvGenActor ! PoisonPill
            cleanup
        }
        case ip: InitPacket => setup
        case data: List[Map[String, Any]] => channel.push(new DataPacket(data))
    }
}