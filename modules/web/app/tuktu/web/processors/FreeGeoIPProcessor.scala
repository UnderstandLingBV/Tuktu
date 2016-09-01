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
 * Searches the geolocation of IP addresses using the freegeoip.net web service
 * or one of its clones.
 * See http://freegeoip.net/ for details and limitations.
 */
class FreeGeoIPProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var ip: String = _
    var freegeoip: String = _
    var format: String = _

    override def initialize(config: JsObject) {
        // Get the name of the field containing the ip to lookup.
        ip = (config \ "ip").as[String]
        // Get the url of the freegeoip service to call.
        freegeoip = (config \ "geoipurl").asOpt[String].getOrElse("http://freegeoip.net")
        // Get the format in which the geolocation data should be returned (i.e., json, csv or xml - default is json).
        format = (config \ "format").asOpt[String].getOrElse("json")
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val listfuture = data.data.map{ datum => 
            val address = evaluateTuktuString(ip, datum)
            lookupIP( address ).map{ result => datum + (resultName -> result) }
        }
        val futurelist = Future.sequence( listfuture )
        futurelist.map{ fl => DataPacket( fl ) }
    })

    def lookupIP(ip: String): Future[Any] = {
        val future = WS.url(freegeoip + "/" + format + "/" + ip).get
        future.map(response =>
            format match {
                case "json" => response.json
                case "csv"  => response.body
                case "xml"  => response.xml.toString
                case _      => "invalid format requested!"
            })
    }
}