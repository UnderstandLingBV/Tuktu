package tuktu.generators

import java.io._
import akka.actor._
import akka.actor.Props
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api._
import scala.io.Codec

case class LinePacket(reader: BufferedReader, line: Int)
case class LineStopPacket(reader: BufferedReader)

/**
 * Actor that reads file immutably and non-blocking
 */
class LineReader(parentActor: ActorRef, fileName: String, encoding: String, startLine: Int, endLine: Option[Int]) extends Actor with ActorLogging {
    var headers: Option[List[String]] = None
    
    def receive() = {
        case ip: InitPacket => {
            // Open the file in reader            
            val reader = file.genericReader(fileName)(Codec.apply(encoding))
            
            // Start processing
            self ! new LinePacket(reader, 0)
        }
        case lsp: LineStopPacket => {
            // Close reader and stop
            lsp.reader.close
            parentActor ! new StopPacket
            self ! PoisonPill
        }
        case pkt: LinePacket => pkt.reader.readLine match {
            case null => self ! new LineStopPacket(pkt.reader) // EOF, stop processing
            case line: String => {
                // Send back to parent for pushing into channel
                if (pkt.line >= startLine) endLine match {
                    case None => parentActor ! line
                    case Some(el) => if (pkt.line <= el) parentActor ! line
                }

                // Continue with next line
                self ! new LinePacket(pkt.reader, pkt.line + 1)
            }
        }
    }
}

/**
 * Streams a file line by line
 */
class LineGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get filename
            val fileName = (config \ "filename").as[String]
            val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
            val startLine = (config \ "start_line").asOpt[Int].getOrElse(0)
            val endLine = (config \ "end_line").asOpt[Int]
            
            // Create actor and kickstart
            val lineGenActor = Akka.system.actorOf(Props(classOf[LineReader], self, fileName, encoding, startLine, endLine))
            lineGenActor ! new InitPacket()
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
        case line: String => channel.push(new DataPacket(List(Map(resultName -> line))))
    }
}