package tuktu.processors.arithmetics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils
import tuktu.api.Parsing._

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
            val result = ArithmeticParser(formula)
            if (doRounding)
                datum + (resultName -> (math rint result * math.pow(10, numberOfDecimals)) / math.pow(10, numberOfDecimals))
            else
                datum + (resultName -> result)
        }
    })
}

/**
 * Computes arithmetics and some generic aggregation functions
 */
class ArithmeticAggregateProcessor(resultName: String) extends BaseProcessor(resultName) {
    var calculate: String = _
    var numberOfDecimals: Int = _
    var doRounding: Boolean = _

    override def initialize(config: JsObject) {
        calculate = (config \ "calculate").as[String]
        numberOfDecimals = (config \ "number_of_decimals").asOpt[Int].getOrElse(0)
        doRounding = (config \ "do_rounding").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Compute on entire DataPacket
        val formula = utils.evaluateTuktuString(calculate, data.data.headOption.getOrElse(Map.empty))
        val res = ArithmeticParser(formula, data.data)
        data.map { datum => datum + (resultName -> res) }
    })
}