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

class ESProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var host = ""
    var port = 9200
    var httpMethod = ""
    var requestBody: Option[JsValue] = None

    var typeField = ""
    var idField: Option[String] = None
    var indexField = ""

    override def initialize(config: JsObject) = {
        // Get parameters required to set up ES HTTP calls        
        host = (config \ "host").as[String]
        port = (config \ "port").as[Int]
        httpMethod = (config \ "http_method").asOpt[String].getOrElse("GET")
        requestBody = (config \ "body").asOpt[JsValue]

        typeField = (config \ "type_field").as[String]
        idField = (config \ "id_field").asOpt[String]
        indexField = (config \ "index_field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {

        // Get all future responses 
        val responses = Future.sequence(for (datum <- data.data) yield {
            // Build URL
            val url = "http://" + host + ":" + port + "/" + datum(indexField) + "/" + datum(typeField) + "/" + {
                idField match {
                    case None        => ""
                    case Some(field) => datum(field)
                }
            }
            val ws = WS.url(url)

            val body = requestBody match {
                case None     => ""
                case Some(rb) => tuktu.api.utils.evaluateTuktuString(rb.toString, datum)
            }

            (httpMethod match {
                case "post"   => ws.withHeaders("Content-Type" -> "application/json").post(body)
                case "put"    => ws.put(body)
                case "delete" => ws.delete()
                case _        => ws.get
            }).map {
                response => response.json
            }
        })

        // Wait for all responses to come in
        val replies = Await.result(responses, timeout.duration)

        // Now combine replies with data
        new DataPacket(
            for ((datum, reply) <- data.data.zip(replies)) yield {
                datum + (resultName -> reply)
            })
    })

}