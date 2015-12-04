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

/**
 * @author dmssrt
 */
class HTTPGetProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var timeout: Int = _

    override def initialize(config: JsObject) {
        // Get the name of the field containing the URL to request and the request timeout        
        field = (config \ "field").as[String]
        timeout = (config \ "timeout").asOpt[Int].getOrElse(Cache.getAs[Int]("timeout").getOrElse(30))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield {
            val urls: Stream[String] = datum(field) match {
                case u: String      => Stream(u)
                case u: JsString    => Stream(u.value)
                case s: Stream[Any] => s.map(anyToString(_))
                case a: Any         => Stream(a.toString)
            }
            datum + (resultName -> urls.map(callService(_)))
        }
    })

    private def anyToString(any: Any): String = any match {
        case s: String   => s
        case s: JsString => s.value
        case s: Any      => s.toString
    }

    def callService(url: String): JsValue = {
        val future = WS.url(url).get
        val body = future.map(response => response.body)
        val result = Await.result(body, timeout seconds)
        Json.parse(result)
    }
}