package tuktu.social.generators

import org.scribe.builder.ServiceBuilder
import org.scribe.builder.api.LinkedInApi
import akka.actor.ActorRef
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import org.scribe.model.Verb
import org.scribe.model.OAuthRequest
import org.scribe.model.Token
import tuktu.api.InitPacket

/**
 * Gets data from a linkedin endpoint
 */
class LinkedinGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get credentials
            val consumerKey = (config \ "credentials" \ "consumer_key").as[String]
            val consumerSecret = (config \ "credentials" \ "consumer_key").as[String]
            val accessToken = (config \ "credentials" \ "access_token").as[String]
            val accessTokenSecret = (config \ "credentials" \ "access_token_secret").as[String]

            // Set up OAuth account
            val service = new ServiceBuilder()
                .provider(classOf[LinkedInApi])
                .apiKey(consumerKey)
                .apiSecret(consumerSecret)
                .build
            // Set up access token
            val token = new Token(accessToken, accessTokenSecret)

            // Get the endpoint to request
            val url = (config \ "url").as[String]
            val httpMethod = {
                (config \ "http_method").asOpt[String].getOrElse("get") match {
                    case "post" => Verb.POST
                    case "delete" => Verb.DELETE
                    case "put" => Verb.PUT
                    case _      => Verb.GET
                }
            }
            
            // Make the request
            val request = new OAuthRequest(httpMethod, url)
            service.signRequest(token, request)
            val response = request.send
            
            if (response.isSuccessful) {
                // See what fields we are after
                val values = (config \\ "field")
                values.foreach(value => channel.push(new DataPacket(List(Map(resultName -> value)))))
            }
            
            // Terminate
            self ! new StopPacket
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}