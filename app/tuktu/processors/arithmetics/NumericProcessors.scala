package tuktu.processors.arithmetics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

class NumberToNumberProcessor(resultName: String) extends BaseProcessor(resultName) {
    var targetType = ""
    var field = ""
    
    override def initialize(config: JsObject) = {
        targetType = (config \ "target_type").as[String]
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Convert the field from source to target
        for (datum <- data) yield datum + (resultName -> converter(datum(field)))
    })
    
    /**
     * Converts a field to the required target type
     */
    private def converter(value: Any): Any = {
        value match {
            case a: String => targetType match {
                case "Long" => a.toLong
                case "Double" => a.toDouble
                case "Float" => a.toFloat
                case "BigDecimal" => BigDecimal(a)
                case _ => a.toInt
            }
            case a: Int => targetType match {
                case "Long" => a.toLong
                case "Double" => a.toDouble
                case "Float" => a.toFloat
                case "BigDecimal" => BigDecimal(a)
                case _ => a.toInt
            }
            case a: Long => targetType match {
                case "Long" => a.toLong
                case "Double" => a.toDouble
                case "Float" => a.toFloat
                case "BigDecimal" => BigDecimal(a)
                case _ => a.toInt
            }
            case a: Double => targetType match {
                case "Long" => a.toLong
                case "Double" => a.toDouble
                case "Float" => a.toFloat
                case "BigDecimal" => BigDecimal(a)
                case _ => a.toInt
            }
            case a: Float => targetType match {
                case "Long" => a.toLong
                case "Double" => a.toDouble
                case "Float" => a.toFloat
                case "BigDecimal" => BigDecimal(a.toDouble)
                case _ => a.toInt
            }
            case a: BigDecimal => targetType match {
                case "Long" => a.toLong
                case "Double" => a.toDouble
                case "Float" => a.toFloat
                case "BigDecimal" => a
                case _ => a.toInt
            }
            case a: Map[Any, Any] => a.map(elem => elem._1 -> converter(elem._2))
            case a: Iterable[Any] => a.map(elem => converter(elem))
        }
    }
}