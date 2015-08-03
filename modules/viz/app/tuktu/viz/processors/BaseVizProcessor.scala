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
import akka.actor.ActorRef
import tuktu.viz.actor.GetChartRequest
import akka.pattern.ask
import scala.concurrent.duration.DurationInt
import play.api.cache.Cache
import akka.util.Timeout
import scala.concurrent.Await
import tuktu.viz.actor.GetChartReply

class BaseVizProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var chartName: String = _
    var historical: Boolean = _
    var expiration: Long = _
    var chartActor: ActorRef = _
    
    override def initialize(config: JsObject) = {
        chartName = (config \ "name").as[String]
        historical = (config \ "historical").asOpt[Boolean].getOrElse(false)
        expiration = (config \ "expiration").asOpt[Long].getOrElse(30 * 1000)
        
        // Get the charting actor
        val chartFuture = Akka.system.actorSelection("user/tuktu.viz.ChartingActor") ? new GetChartRequest(chartName, expiration, historical, true)
        val result = Await.result(chartFuture.asInstanceOf[Future[GetChartReply]], timeout.duration)
        chartActor = result.actor
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Go over all data and send to the actor
        data.data.foreach(datum =>
            chartActor ! mapToGraphItem(datum)
        )
        
        data
    })
    
    def mapToGraphItem(map: Map[String, Any]): JsObject = ???
}