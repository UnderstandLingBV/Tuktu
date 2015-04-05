package tuktu.http.processors

import play.api.libs.iteratee.Enumeratee
import tuktu.api._
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.Play.current
import play.api.libs.ws.WSResponse
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.util.Timeout
import play.api.cache.Cache

/**
 * Makes REST requests to a URL
 */
class RESTProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var url = ""
    var port = 9200
    var httpMethod = ""
    var requestBody: Option[JsValue] = None

    override def initialize(config: JsObject) = {
        // Get URL, port and REST options        
        url = (config \ "url").as[String]
        port = (config \ "port").as[Int]
        httpMethod = (config \ "http_method").asOpt[String].getOrElse("GET")
        requestBody = (config \ "body").asOpt[JsValue]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Get all future responses 
        val responses = Future.sequence(for (datum <- data.data) yield {
            // Build URL
            val requestUrl = tuktu.api.utils.evaluateTuktuString(url, datum) + ":" + port
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

        // Wait for all responses to come in
        val replies = Await.result(responses, timeout.duration)

        // Now combine replies with data
        new DataPacket(
            for ((datum, reply) <- data.data.zip(replies)) yield {
                datum + (resultName -> reply)
            }
        )
    })

}