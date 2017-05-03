package tuktu.social.generators

import tuktu.api.BaseGenerator
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import tuktu.api._
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.apis.PinterestApi
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.Verb
import play.api.libs.json.Json
import play.api.libs.json.JsObject

class PinterestGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    /**
     * How to obtain an access token:
     * 
     * import com.github.scribejava.apis.PinterestApi
     * import com.github.scribejava.core.builder.ServiceBuilder
     * 
     * val key = ""
     * val secret = ""
     * val url = "https://understandling.com/"
     * val service = new ServiceBuilder().apiKey(key).apiSecret(secret).scope("read_public").callback(url).build(PinterestApi.instance)
     * service.getAuthorizationUrl
     * // Get the token from the redirect and put in the URL
     * val code = ""
     * val accessToken = service.getAccessToken(code)
     */
    
    override def _receive = {
        case config: JsValue => {
            // Get key and secret
            val key = (config \ "key").as[String]
            val secret = (config \ "secret").as[String]
            // Same for token
            val aToken = (config \ "token").as[String]
            // Get board to track
            val board = (config \ "board").as[String]
            
            // Set up client
            val service = new ServiceBuilder()
                .apiKey(key)
                .apiSecret(secret)
                .scope("read_public")
                .build(PinterestApi.instance)
            // Token
            val token = new OAuth2AccessToken(aToken, "")
                
            // Search board
            val request = new OAuthRequest(Verb.GET, "https://api.pinterest.com/v1/boards/" + board + "/pins?fields=id,link,url,creator,created_at,note,color,counts,media,attribution,image,metadata&access_token=" + token.getAccessToken)
            service.signRequest(token, request)
            val response = service.execute(request)
            if (response.getCode == 200) {
                val json = (Json.parse(response.getBody) \ "data").as[List[JsObject]]
                json.foreach{obj =>
                    channel.push(DataPacket(List(Map(resultName -> obj))))
                }
            }
        }
    }
}