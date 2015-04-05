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
    var host = ""
    var port = 9200
    var httpMethod = ""
    var requestBody: Option[JsValue] = None

    // ES-specific fields
    var typeField = ""
    var idField: Option[String] = None
    var indexField = ""
    
    // The JSON field to obtain
    var jsonField = ""

    override def initialize(config: JsObject) = {
        // Get parameters required to set up ES HTTP calls        
        host = (config \ "host").as[String]
        port = (config \ "port").as[Int]
        httpMethod = (config \ "http_method").asOpt[String].getOrElse("GET")
        requestBody = (config \ "body").asOpt[JsValue]

        // ES fields
        typeField = (config \ "type_field").as[String]
        idField = (config \ "id_field").asOpt[String]
        indexField = (config \ "index_field").as[String]
        
        // The JSON field
        jsonField = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Get all future responses 
        val responses = Future.sequence(for (datum <- data.data) yield {
            // Build URL, host and port can be read from data
            val url = "http://" + tuktu.api.utils.evaluateTuktuString(host, datum) + ":" +
                tuktu.api.utils.evaluateTuktuString(port.toString, datum) + "/" + datum(indexField) + "/" + datum(typeField) + "/" + {
                    // Add ID only if it is set
                    idField match {
                        case None        => ""
                        case Some(field) => datum(field)
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
                case "delete" => ws.delete()
                case _        => ws.get
            }).map {
                // Get all the occurrences of the field we are after
                response => (response.json \\ jsonField)
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