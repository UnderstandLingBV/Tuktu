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

class TDFSBinaryReaderGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get file parameters
            val filename = {
                val f = (config \ "filename").as[String]
                if (f.startsWith("tdfs://")) f.drop("tdfs://".size)
                else f
            }
            
            // Ask for the actual content from the TDFS daemon
            Akka.system.actorSelection("user/tuktu.dfs.Daemon") ! new TDFSReadInitiateRequest(
                    filename, true, None            
            )
        }
        case tcp: TDFSContentPacket => channel.push(new DataPacket(List(Map(resultName -> tcp.content))))
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
        case dpp: DecreasePressurePacket => decBP
        case bpp: BackPressurePacket => backoff
    }
}