package tuktu.viz.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.actor.ActorSelection.toScala
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

class BaseVizProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Go over all data and send to the actor
        data.data.foreach(datum =>
            Akka.system.actorSelection("tuktu.viz.ChartingActor") ! mapToGraphItem(datum)
        )
        
        data
    })
    
    def mapToGraphItem(map: Map[String, Any]): JsObject = ???
}