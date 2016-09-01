package tuktu.ml.processors.preprocessing

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsValue

/**
 * Replaces missing values for specific data types with target values
 */
class MissingvaluesProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: Option[List[String]] = _
    var replacements: List[(String, JsValue)] = _
    
    override def initialize(config: JsObject) {
        fields = (config \ "fields").asOpt[List[String]]
        replacements = (config \ "replacements").as[List[JsObject]].map(elem =>
            ((elem \ "type").as[String], (elem \ "target").as[JsValue])
        )
    }
    
    def replaceValues(value: Any, repls: List[(String, JsValue)]): Any = repls match {
        case Nil => value
        case rep::trail => replaceValues({
                val isEmpty = (value.toString.isEmpty || value.toString == "" || value.toString == "null")
                if (isEmpty) replaceValue(rep._1, value, rep._2)
                else value
            }, trail)
    }
    
    def replaceValue(t: String, value: Any, target: JsValue) = value match {
        case v: Int if t == "int" => target.as[Int]
        case v: Long if t == "long" => target.as[Long]
        case v: Float if t == "float" => target.as[Float]
        case v: Double if t == "double" => target.as[Double]
        case v: Byte if t == "byte" => target.as[Int].toByte
        case v: Short if t == "short" => target.as[Short]
        case v: String if t == "string" => target.as[String]
        case v: Any if t == "any" => target
        case _ => value
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            datum.map(elem => {
                elem._1 -> (fields match {
                    case Some(fs) if fs.contains(elem._1) => replaceValues(elem._2, replacements)
                    case _ => replaceValues(elem._2, replacements)
                })
            })
        }
    })
}