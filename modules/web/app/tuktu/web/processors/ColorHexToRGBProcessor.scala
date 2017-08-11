package tuktu.web.processors

import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.utils
import java.awt.Color
import scala.concurrent.Future

class ColorHexToRGBProcessor(resultName: String) extends BaseProcessor(resultName) {
    var hex: String = _
    
    override def initialize(config: JsObject) {
        hex = (config \ "hex").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(data.data.map {datum =>
            val color = Color.decode(utils.evaluateTuktuString(hex, datum))
            datum + (resultName -> List(color.getRed, color.getGreen, color.getBlue))
        })
    })
}