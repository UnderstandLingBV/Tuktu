package tuktu.processors.arithmetics

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global

class MaxFieldByValueProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields: List[String] = _
    override def initialize(config: JsObject) = {
        fields = (config \ "fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(data.data.map {datum =>
            datum + (resultName -> {
                (fields.map {field =>
                    (field, datum(field) match {
                        case a: Int => a.toDouble
                        case a: Long => a.toDouble
                        case a: Double => a
                        case _ => datum(field).toString.toDouble
                    })
                } maxBy {_._2})._1
            })
        })
    })
}