package tuktu.csv.generators.flattening

import java.io.File
import java.io.FileInputStream
import scala.collection.JavaConversions.asScalaIterator
import akka.actor._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import play.api.libs.json.JsArray
import au.com.bytecode.opencsv.CSVReader
import java.io.FileReader
import tuktu.api.BaseGenerator

class CsvReader(parentActor: ActorRef, fileName: String, valueName: String,
        dataColStart: Int, dataColEnd: Option[Int], hierarchy: List[ParseNode], endFieldCol: Int, endField: String,
        separator: Char, quote: Char, escape: Char
) extends Actor with ActorLogging {
    
    def receive() = {
        case ip: InitPacket => {
            // Open the CSV file for reading
            val reader = new CSVReader(new FileReader(fileName), separator, quote, escape)
            
            // Start processing
            self ! new CSVReadPacket(reader, 0)
        }
        case sp: CSVStopPacket => {
            // Close CSV reader, kill parent and self
            sp.reader.close
            parentActor ! new StopPacket
        }
        case pkt: CSVReadPacket => pkt.reader.readNext match {
            case null => self ! new CSVStopPacket(pkt.reader) // EOF, stop processing
            case csvLine: Array[String] => {
                // Get the row as a list of String
                val line = csvLine.toList
                
                // See if this is the end or not
                if (line(endFieldCol) == endField) {
                    // We may need to stop preliminary if we find the end field
                    self ! new CSVStopPacket(pkt.reader)
                }
                else {
                    val endPos = dataColEnd match {
                        case Some(end) => end
                        case None => line.size - 1
                    }
                    // Go through cells of this row
                    for (i <- 0 to endPos) {
                        // Apply parsers on this cell
                        val flattenedMap = (for (pn <- hierarchy) yield {
                            pn.name -> pn.locator(line, pkt.rowOffset, i)
                        }).toMap
                        
                        // Only do something if we have all values
                        if (i >= dataColStart && flattenedMap.forall(elem => elem._2 != null)) {
                            // Send back to parent
                            val mapToSend = (flattenedMap + (valueName -> line(i)))
                            parentActor ! mapToSend
                        }
                    }
	                
	                // Continue with next line
	                self ! new CSVReadPacket(pkt.reader, pkt.rowOffset + 1)
                }
            }
        }
    }
}

class CsvGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    private var flattened = false
    
	override def receive() = {
	    case config: JsValue => {
	        // Get filename, sheet name and data start
	        val fileName = (config \ "filename").as[String]
            val valueName = (config \ "value_name").as[String]
            
            // Get hierarchy
            val hierarchy = Common.parseHierarchy((config \ "locators").as[List[JsObject]])
            
	        // Flattened or not
	        flattened = (config \ "flattened").asOpt[Boolean].getOrElse(false)
            val dataColStart = (config \ "data_start_col").as[Int]
            val dataColEnd = (config \ "data_end_col").asOpt[Int]
	        val endFieldCol = (config \ "end_field" \ "column").as[Int]
	        val endField = (config \ "end_field" \ "value").as[String]
            
            // Get separator, quote and escape char
            val separator = (config \ "separator").asOpt[String].getOrElse(";").head
            val quote = (config \ "quote").asOpt[String].getOrElse("\"").head
            val escape = (config \ "escape").asOpt[String].getOrElse("\\").head
	        
	        // Create actor and kickstart
	        val csvGenActor = Akka.system.actorOf(Props(classOf[CsvReader], self, fileName, valueName,
                    dataColStart, dataColEnd, hierarchy, endFieldCol, endField,
                    separator, quote, escape))
	        csvGenActor ! new InitPacket()
	    }
	    case sp: StopPacket => {
            channel.eofAndEnd
            self ! PoisonPill
        }
	    case headerfullLine: Map[String, String] => flattened match {
	        case false => channel.push(new DataPacket(List(Map(resultName -> headerfullLine))))
	        case true => channel.push(new DataPacket(List(headerfullLine)))
	    }
	}
}