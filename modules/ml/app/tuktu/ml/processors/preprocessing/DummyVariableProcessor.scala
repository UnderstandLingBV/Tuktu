package tuktu.ml.processors.preprocessing

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DummyVariableProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var max: Int = _
    var asList: Boolean = _
    
    override def initialize(config: JsObject) {
        field = (config \ "field").as[String]
        max = (config \ "max").as[Int]
        asList = (config \ "as_list").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(data.data.map { datum =>
            datum + (resultName -> {
                val value = datum(field).toString.toInt
                val dummies = (0 to max).toList.map {i =>
                    if (i == value) 1 else 0
                }
                if (!asList) dummies.mkString("") else dummies
            })
        })
    })
}