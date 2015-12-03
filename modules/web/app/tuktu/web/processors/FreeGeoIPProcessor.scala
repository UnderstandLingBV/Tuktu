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
 * Searches the geolocation of IP addresses using the freegeoip.net web service
 * or one of its clones.
 * See http://freegeoip.net/ for details and limitations.
 */
class FreeGeoIPProcessor(resultName: String) extends BaseProcessor(resultName) {
    var ipfield: String = _
    var freegeoip: String = _
    var format: String = _
    var timeout: Int = _

    override def initialize(config: JsObject) {
        // Get the name of the field containing the ip to lookup.
        ipfield = (config \ "ipfield").as[String]
        // Get the url of the freegeoip service to call.
        freegeoip = (config \ "geoipurl").asOpt[String].getOrElse("http://freegeoip.net")
        // Get the format in which the geolocation data should be returned (i.e., json, csv or xml - default is json).
        format = (config \ "format").asOpt[String].getOrElse("json")
        // Get the timeout of the service
        timeout = (config \ "timeout").asOpt[Int].getOrElse(Cache.getAs[Int]("timeout").getOrElse(30))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for (datum <- data.data) yield {
            val ips: Stream[String] = datum(ipfield) match {
                case u: String      => Stream(u)
                case u: JsString    => Stream(u.value)
                case s: Stream[Any] => s.map(anyToString(_))
                case a: Any         => Stream(a.toString)
            }
            datum + (resultName -> ips.map(lookupIP(_)))
        })
    })

    private def anyToString(any: Any): String = any match {
        case s: String   => s
        case s: JsString => s.value
        case s: Any      => s.toString
    }

    def lookupIP(ip: String): Any = {
        val future = WS.url(freegeoip + "/" + format + "/" + ip).get
        val body = future.map(response => response.body)
        val result = Await.result(body, timeout seconds)
        format match {
            case "json" => Json.parse(result)
            case "csv"  => result
            case "xml"  => result
            case _      => "invalid format requested!"
        }

    }
}