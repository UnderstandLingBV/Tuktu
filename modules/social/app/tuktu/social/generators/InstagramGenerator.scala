package tuktu.social.generators

import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb

import akka.actor.ActorRef
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.social.scribe.InstagramApi20
import play.api.libs.json.JsObject

class InstagramGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    /**
     * How to obtain an access token:
     * 
     * import tuktu.social.scribe.InstagramApi20
     * import com.github.scribejava.core.builder.ServiceBuilder
     * import play.api.libs.ws.WS
     * import play.api.{Play, Mode, DefaultApplication}
     * import java.io.File
     * 
     * val key = ""
     * val secret = ""
     * val url = "https://understandling.com/"
     * val service = new ServiceBuilder().apiKey(key).apiSecret(secret).scope("public_content").callback(url).build(InstagramApi20.instance)
     * service.getAuthorizationUrl
     * // Get the token from the redirect and put in the URL
     * val code = ""
     * implicit val application = new DefaultApplication(new File("."), Thread.currentThread().getContextClassLoader(), None, Mode.Dev)
     * import scala.concurrent.ExecutionContext.Implicits.global
     * WS.url("https://api.instagram.com/oauth/access_token").post(Map("client_id" -> Seq(key), "client_secret" -> Seq(secret), "grant_type" -> Seq("authorization_code"), "redirect_url" -> Seq(url), "code" -> Seq(code))) map { response => println(response.body) }
     */
    
    override def _receive = {
        case config: JsValue => {
            // Get key and secret
            val key = (config \ "key").as[String]
            val secret = (config \ "secret").as[String]
            // Same for token
            val aToken = (config \ "token").as[String]
            
            // Get keyword
            val keyword = (config \ "keyword").as[String]
            
            // Set up service
            val service = new ServiceBuilder()
                .apiKey(key)
                .apiSecret(secret)
                .scope("public_content")
                .build(InstagramApi20.instance)
            // Token
            val token = new OAuth2AccessToken(aToken, "")
            
            // Make the request
            val request = new OAuthRequest(Verb.GET, "https://api.instagram.com/v1/tags/" + keyword + "/media/recent?access_token=" + token.getAccessToken)
            // SIgn and send
            service.signRequest(token, request)
            val response = service.execute(request)
            
            if (response.getCode == 200) {
                val data = (Json.parse(response.getBody) \ "data").as[List[JsObject]]
                data.foreach {json =>
                    channel.push(new DataPacket(List(Map(resultName -> json))))
                }
            }
        }
    }
}