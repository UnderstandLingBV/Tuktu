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
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import tuktu.api.BaseGenerator

case class CSVReadPacket(
        reader: CSVReader
)
case class CSVStopPacket(
        reader: CSVReader
)

class CsvReader(parentActor: ActorRef, fileName: String, hasHeaders: Boolean, givenHeaders: List[String],
        separator: Char, quote: Char, escape: Char) extends Actor with ActorLogging {
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
            // Close CSV reader, kill parent and self
            sp.reader.close
            parentActor ! new StopPacket
            self ! PoisonPill
        }
        case pkt: CSVReadPacket => pkt.reader.readNext match {
            case null => self ! new CSVStopPacket(pkt.reader) // EOF, stop processing
            case line: Array[String] => {
                // Send back to parent for pushing into channel
                headers match {
                    case Some(hdrs) => {
                        parentActor ! hdrs.zip(line.toList).toMap
                    }
                    case None => parentActor ! line
                }
                // Continue with next line
                self ! pkt
            }
        }
    }
}

class CSVGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    private var flattened = false
    override def receive() = {
        case config: JsValue => {
            // Get filename
            val fileName = (config \ "filename").as[String]
            val hasHeaders = (config \ "has_headers").asOpt[Boolean].getOrElse(false)
            val headersGiven = (config \ "predef_headers").asOpt[List[String]].getOrElse(List())
            flattened = (config \ "flattened").asOpt[Boolean].getOrElse(false)
            val separator = (config \ "separator").asOpt[String].getOrElse(";").head
            val quote = (config \ "quote").asOpt[String].getOrElse("\"").head
            val escape = (config \ "escape").asOpt[String].getOrElse("\\").head
            
            // Create actor and kickstart
            val csvGenActor = Akka.system.actorOf(Props(classOf[CsvReader], self, fileName, hasHeaders, headersGiven, separator, quote, escape))
            csvGenActor ! new InitPacket()
        }
        case sp: StopPacket => {
            cleanup()
        }
        case headerlessLine: Array[String] => channel.push(new DataPacket(List(Map(resultName -> headerlessLine.toList))))
        case headerfullLine: Map[String, String] => flattened match {
            case false => channel.push(new DataPacket(List(Map(resultName -> headerfullLine))))
            case true => channel.push(new DataPacket(List(headerfullLine)))
        }
    }
}