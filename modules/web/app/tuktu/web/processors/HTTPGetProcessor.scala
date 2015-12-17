package tuktu.web.processors

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._

import play.api.libs.ws.WS

import play.api.Play.current

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import tuktu.api._
import tuktu.api.utils.evaluateTuktuString

/**
 * @author dmssrt
 */
class HTTPGetProcessor(resultName: String) extends BaseProcessor(resultName) {
    var url: String = _

    override def initialize(config: JsObject) {
        // Get the name of the field containing the URL to request and the request timeout        
        url = (config \ "url").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) =>{
        val listfuture = data.data.map{ datum => 
            val query = evaluateTuktuString(url, datum)
            val futureresult = callService( query )
            futureresult.map{ result => datum + (resultName -> result) }
        }
        val futurelist = Future.sequence( listfuture )
        futurelist.map{ fl => new DataPacket( fl ) }
    })

    def callService(url: String): Future[JsValue] = {
        val future = WS.url(url).get
        val body = future.map(response => response.body)
        body.map{ result => Json.parse(result) }
    }
}