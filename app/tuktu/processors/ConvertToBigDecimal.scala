package tuktu.processors

import scala.BigDecimal
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Converts a field to a BigDecimal.
 */
class ConvertToBigDecimal(resultName: String) extends BaseProcessor(resultName) {
    /** The field containing the value to be converted to BigDecimal. */
    var field: String = _

    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            val bigDecimalVal = datum(field) match {
                case g: String   => BigDecimal(g)
                case g: Integer  => BigDecimal(g)
                case g: Long     => BigDecimal(g)
                case g: Double   => BigDecimal(g)
                case g: Char     => BigDecimal(g)
                case g: JsString => BigDecimal(g.value)
                case g: Seq[Any] => anyListToBigDecimal(g)
                case g: Any      => BigDecimal(g.toString)
            }
            datum + (field -> bigDecimalVal)
        })
    })

    def anyListToBigDecimal(list: Seq[Any]): Seq[BigDecimal] = list match {
        case Nil => Nil
        case elem :: remainder => Seq(elem match {
            case g: String   => BigDecimal(g)
            case g: Integer  => BigDecimal(g)
            case g: Long     => BigDecimal(g)
            case g: Double   => BigDecimal(g)
            case g: Char     => BigDecimal(g)
            case g: JsString => BigDecimal(g.value)
            case g: Any      => BigDecimal(g.toString)
        }) ++ anyListToBigDecimal(remainder)
    }
}