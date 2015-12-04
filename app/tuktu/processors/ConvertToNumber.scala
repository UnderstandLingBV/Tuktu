package tuktu.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import java.text.NumberFormat
import java.util.Locale

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.{ JsObject, JsString }
import tuktu.api.{ BaseProcessor, DataPacket }

/**
 * Converts a field to a specified number type.
 */
class ConvertToNumber(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var locale: NumberFormat = _
    var numberType: String = _

    override def initialize(config: JsObject) = {
        field = (config \ "field").as[String]
        locale = NumberFormat.getInstance(new Locale((config \ "locale").asOpt[String].getOrElse("en")))
        numberType = (config \ "number_type").asOpt[String].getOrElse("double")
    }

    override def processor: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            val result = datum(field) match {
                // Convert each element of a sequence
                case s: Seq[Any] => s.map(any => numberToType(anyToNumber(any)))
                case any: Any    => numberToType(anyToNumber(any))
            }
            datum + (resultName -> result)
        }
    })

    private def anyToNumber(any: Any): Number = any match {
        case g: Number   => g
        case g: Byte     => g
        case g: Char     => g
        case g: Short    => g
        case g: Int      => g
        case g: Long     => g
        case g: Float    => g
        case g: Double   => g
        case g: JsString => locale.parse(g.value)
        case g: Any      => locale.parse(g.toString)
    }

    private def numberToType(n: Number): Any = numberType.toLowerCase match {
        case "byte"  => n.byteValue
        case "short" => n.shortValue
        case "int"   => n.intValue
        case "long"  => n.longValue
        case "float" => n.floatValue
        case _       => n.doubleValue
    }
}