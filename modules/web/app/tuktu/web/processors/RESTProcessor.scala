package tuktu.web.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import tuktu.api._
import tuktu.api.BaseProcessor

/**
 * Makes REST requests to a URL
 */
class RESTProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var url = ""
    var httpMethod = ""
    var requestBody: Option[JsValue] = None

    override def initialize(config: JsObject) {
        // Get URL, port and REST options        
        url = (config \ "url").as[String]
        httpMethod = (config \ "http_method").asOpt[String].getOrElse("get")
        requestBody = (config \ "body").asOpt[JsValue]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Get all future responses 
        val responses = Future.sequence(for (datum <- data.data) yield {
            // Build URL
            val requestUrl = tuktu.api.utils.evaluateTuktuString(url, datum)
            // Get web service
            val ws = WS.url(requestUrl)

            // Set up body
            val body = requestBody match {
                case None     => ""
                case Some(rb) => tuktu.api.utils.evaluateTuktuString(rb.toString, datum)
            }

            // Return the response as future
            (httpMethod match {
                case "post"   => ws.post(body)
                case "put"    => ws.put(body)
                case "delete" => ws.delete()
                case _        => ws.get
            }).map {
                response => response.body
            }
        })

        // Now combine replies with data
        responses.map(replies => {
            DataPacket(
                for ((datum, reply) <- data.data.zip(replies)) yield {
                    datum + (resultName -> reply)
                }
            )
        })
    })

}