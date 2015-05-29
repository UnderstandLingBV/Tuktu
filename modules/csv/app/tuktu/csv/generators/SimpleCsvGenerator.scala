package tuktu.csv.generators

import java.io.FileReader
import akka.actor.ActorRef
import au.com.bytecode.opencsv.CSVReader
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import tuktu.api.InitPacket


class SimpleCSVGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
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
            
            // Open CSV file
            val reader = new CSVReader(new FileReader(fileName), separator, quote, escape)
            
            // See if we need to fetch headers
            val headers = {
                if (hasHeaders) Some(reader.readNext.toList)
                else {
                    if (headersGiven != List()) Some(headersGiven)
                    else None
                }
            }

            // Process line by line
            var line = reader.readNext
            while (line != null) {
                // Send back to parent for pushing into channel
                headers match {
                    case Some(hdrs) => {
                        val mapLine = hdrs.zip(line.toList).toMap
                        flattened match {
                            case false => channel.push(new DataPacket(List(Map(resultName -> mapLine))))
                            case true => channel.push(new DataPacket(List(mapLine)))
                        }
                    }
                    case None => channel.push(new DataPacket(List(Map(resultName -> line.toList))))
                }
                
                // Continue with next line
                line = reader.readNext
            }
            
            // CLose file
            reader.close
            self ! new StopPacket
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}