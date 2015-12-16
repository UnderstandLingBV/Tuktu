package tuktu.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.{ JsObject, JsString }
import tuktu.api.{ BaseProcessor, DataPacket }

/**
 * Converts a field to a String.
 */
class ConvertAnyToStringProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _

    override def initialize(config: JsObject) = {
        field = (config \ "field").as[String]
    }

    override def processor: Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            val result = datum(field) match {
                // Convert each element of a sequence
                case s: Seq[Any] => s.map(any => anyToString(any))
                case any: Any    => anyToString(any)
            }
            datum + (resultName -> result)
        }
    })

    private def anyToString(any: Any): String = any match {
        case a: String   	=> a
        case a: JsString  => a.as[String]
        case a: Any		    => a.toString
    }

}