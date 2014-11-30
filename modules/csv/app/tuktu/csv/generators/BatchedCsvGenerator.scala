package tuktu.csv.generators

import java.io.FileReader

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
import tuktu.api.AsyncGenerator
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket


case class HeaderLessList(
        data: List[Array[String]]
)
case class HeaderFulList(
        data: List[Map[String, String]]
)

class BatchedCsvReader(parentActor: ActorRef, fileName: String, hasHeaders: Boolean, givenHeaders: List[String],
        separator: Char, quote: Char, escape: Char, batchSize: Integer, flattened: Boolean, resultName: String) extends Actor with ActorLogging {
    var batch = collection.mutable.Queue[Array[String]]()
    var headers: Option[List[String]] = None
    
    def receive() = {
        case ip: InitPacket => {
            // Open CSV file for reading
            val reader = new CSVReader(new FileReader(fileName), separator, quote, escape)
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
                // Add this to our batch
                batch += line
                
                // Flush if we have to
                if (batch.size == batchSize) {
                    // Send it back
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
                    parentActor ! new DataPacket(data)
                    
                    // Clear batch
                    batch.clear
                }
                
                // Continue with next line
                self ! pkt
            }
        }
    }
}

class BatchedCSVGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends AsyncGenerator(resultName, processors) {
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
            val batchSize = (config \ "batch_size").asOpt[Int].getOrElse(100)
            
            // Create actor and kickstart
            val csvGenActor = Akka.system.actorOf(Props(classOf[BatchedCsvReader], self, fileName, hasHeaders, headersGiven, separator, quote, escape, batchSize, flattened, resultName))
            csvGenActor ! new InitPacket()
        }
        case sp: StopPacket => {
            cleanup()
        }
        case packet: DataPacket => channel.push(packet)
    }
}