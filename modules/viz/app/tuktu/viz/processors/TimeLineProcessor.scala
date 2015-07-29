package tuktu.viz.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.actor.ActorSelection.toScala
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import tuktu.api.DataPacket

class TimeLineProcessor(resultName: String) extends BaseVizProcessor(resultName) {
    var timeField: Option[String] = _
    var dataField: String = _ 
    
    override def initialize(config: JsObject) = {
        timeField = (config \ "time_field").asOpt[String]
        dataField = (config \ "data_field").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        val nowTime = timeField match {
            case Some(time) => 0
            case None => System.currentTimeMillis / 1000L
        }
        
        // Get actor once
        val actorSel = Akka.system.actorSelection("user/tuktu.viz.ChartingActor")
        
        // Go over all data and send to the actor
        data.data.foreach(datum =>
            actorSel ! Json.obj(
                    "time" -> {
                        timeField match {
                            case Some(time) => datum(time).asInstanceOf[Long]
                            case None => nowTime
                        }
                    }, "y" -> datum(dataField).asInstanceOf[Double]
            )
        )
        
        data
    })
}