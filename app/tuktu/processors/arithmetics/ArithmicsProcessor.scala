package tuktu.processors.arithmetics

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.utils
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Calculates arithmetic
 */
class ArithmeticProcessor(resultName: String) extends BaseProcessor(resultName) {  
    var calculate: String = _
    
    override def initialize(config: JsObject) = {
        calculate = (config \ "calculate").as[String]          
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {
          new DataPacket(for (datum <- data.data) yield {
            val formula = utils.evaluateTuktuString(calculate, datum)
            val result = tuktu.utils.ArithmeticParser.readExpression(formula)
            if (result.isDefined)
              datum + (resultName -> result.get())
            else datum
          })
        }
    })  
}