package tuktu.processors.arithmetics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils

/**
 * Calculates arithmetic
 */
class ArithmeticProcessor(resultName: String) extends BaseProcessor(resultName) {
    var calculate: String = _
    var numberOfDecimals: Int = _
    var doRounding: Boolean = _

    override def initialize(config: JsObject) {
        calculate = (config \ "calculate").as[String]
        numberOfDecimals = (config \ "number_of_decimals").asOpt[Int].getOrElse(0)
        doRounding = (config \ "do_rounding").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            val formula = utils.evaluateTuktuString(calculate, datum)
            val result = tuktu.utils.ArithmeticParser.readExpression(formula)
            if (result.isDefined)
                if (doRounding)
                    datum + (resultName -> (math rint result.get() * math.pow(10, numberOfDecimals)) / math.pow(10, numberOfDecimals))
                else
                    datum + (resultName -> result.get())
            else
                datum
        }
    })
}