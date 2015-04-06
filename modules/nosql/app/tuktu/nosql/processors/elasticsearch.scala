package tuktu.nosql.processors

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
 * Makes an HTTP request to ES, using the REST API
 */
class ESProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    // HTTP fields
    var urlField = ""
    var httpMethod = ""
    var requestBody: Option[JsValue] = None

    // The JSON field to obtain
    var jsonField = ""

    override def initialize(config: JsObject) = {
        // Get parameters required to set up ES HTTP calls
        urlField = (config \ "url").as[String]
        httpMethod = (config \ "http_method").asOpt[String].getOrElse("get")
        requestBody = (config \ "body").asOpt[JsValue]
        
        // The JSON field
        jsonField = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Get all future responses 
        val responses = Future.sequence(for (datum <- data.data) yield {
            // Build URL, host and port can be read from data
            val url = {
                val evalUrl = tuktu.api.utils.evaluateTuktuString(urlField, datum)
                // check if http need to be appended
                if (!evalUrl.toLowerCase.startsWith("http")) {
                    "http://" + evalUrl
                } else {
                    evalUrl
                }                
            } 
                            
            val ws = WS.url(url)

            // Do we need to append a body or not?
            val body = requestBody match {
                case None     => ""
                case Some(rb) => tuktu.api.utils.evaluateTuktuString(rb.toString, datum)
            }

            // Make the actual call, note that if ES returns something, it is JSON
            (httpMethod match {
                case "post"   => ws.withHeaders("Content-Type" -> "application/json").post(body)
                case "put"    => ws.put(body)
                case "delete" => ws.delete
                case _        => ws.get
            }).map {
                // Get all the occurrences of the field we are after                
                response => {((response.json match {
                        case jsonString: JsValue => jsonString
                        case null => Json.parse(response.body)
                    }) \\ tuktu.api.utils.evaluateTuktuString(jsonField,datum))                    
                }
            }
        })

        // Wait for all responses to come in
        val replies = Await.result(responses, timeout.duration)

        // Now combine replies with data
        new DataPacket(
            for {
                    (datum, replyList) <- data.data.zip(replies)
                    reply <- replyList
            } yield {
                datum + (resultName -> reply)
            }
        )
    })
}