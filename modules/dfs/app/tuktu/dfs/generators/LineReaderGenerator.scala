package tuktu.dfs.generators

import akka.actor.ActorRef
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import tuktu.dfs.actors.TDFSReadInitiateRequest
import play.api.libs.concurrent.Akka
import play.api.Play.current
import tuktu.dfs.actors.TDFSContentPacket
import tuktu.api.BackPressurePacket
import tuktu.api.DecreasePressurePacket

/**
 * Reads a file from TDFS line by line
 */
class TDFSLineReaderGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var lineOffset = 0
    
    override def receive() = {
        case config: JsValue => {
            // Get file parameters
            val filename = (config \ "filename").as[String]
            val encoding = (config \ "encoding").asOpt[String]
            // Get start/end line and such
            val start = (config \ "start_line").asOpt[Int].getOrElse(0)
            val end = (config \ "end_line").asOpt[Int]
            
            // Ask for the actual content from the TDFS daemon
            Akka.system.actorSelection("user/tuktu.dfs.Daemon") ! new TDFSReadInitiateRequest(
                    filename, false, encoding
            )
        }
        case tcp: TDFSContentPacket => {
            // Check start- and end line
            //if (lineOffset >= startLine && lineOffset <= endLine)
            // Make a proper string and output it
            channel.push(new DataPacket(List(Map(resultName -> new String(tcp.content)))))
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
        case dpp: DecreasePressurePacket => decBP
        case bpp: BackPressurePacket => backoff
    }
}