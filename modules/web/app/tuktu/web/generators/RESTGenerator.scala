package tuktu.web.generators

import tuktu.api._
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import play.api.libs.ws.WS
import play.api.Play.current
import play.api.libs.ws.WSResponse
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Gets a webpage's content based on REST request
 */
class RESTGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def _receive = {
        case config: JsValue => {
            // Get required config params to make request
            val url = (config \ "url").as[String]
            val port = (config \ "port").asOpt[Int].getOrElse(80)
            val httpMethod = (config \ "http_method").asOpt[String].getOrElse("GET")
            val requestBody = (config \ "body").asOpt[JsValue]
            // To add status code or not? and in what field?
            val addCode = (config \ "add_code").asOpt[Boolean].getOrElse(false)
            val codeField = (config \ "code_field").asOpt[String].getOrElse("")
            // How to parse response body
            val parseAs = (config \ "parse_as").asOpt[String].getOrElse("text")
            
            // Make the actual call
            val requestUrl = url + ":" + port
            // Get web service
            val ws = WS.url(requestUrl)
    
            // Set up body
            val body = requestBody match {
                case None     => ""
                case Some(rb) => rb.toString
            }
    
            // Get response
            val response = httpMethod match {
                case "post"   => ws.post(body)
                case "put"    => ws.put(body)
                case "delete" => ws.delete()
                case _        => ws.get
            }
            
            // Push result on success, then push EOF
            response.onSuccess {
                case resp: WSResponse => {
                    channel.push(new DataPacket(
                        List(
                            Map(
                                resultName -> (parseAs match {
                                    // See how to parse
                                    case "json" => resp.json
                                    case "xml" => resp.xml
                                    case _ => resp.body
                                })
                            ) ++ {
                                if (addCode) {
                                    // Add the status code too
                                    Map(codeField -> resp.status)
                                } else Map()
                            }
                        )
                    ))
                }
                case _ => new StopPacket
            }
            
            // If failure occurs, just stop
            response.onFailure {
                case _ => new StopPacket
            }
        }
    }
}