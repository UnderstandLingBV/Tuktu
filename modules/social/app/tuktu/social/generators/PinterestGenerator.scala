package tuktu.social.generators

import com.github.scribejava.apis.PinterestApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth20Service

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.routing.RoundRobinPool

import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import tuktu.api._
import tuktu.api.BaseGenerator
import akka.routing.Broadcast


case class PinterestRequest(
        board: String,
        cursor: String
)
case class PinterestObjects(
        data: List[JsObject]
)

class AsyncPinterestActor(parent: ActorRef, client: OAuth20Service, token: OAuth2AccessToken, start: Option[Long], end: Option[Long]) extends Actor with ActorLogging {
    def receive() = {
        case pr: PinterestRequest => {
            // Search board
            val request = new OAuthRequest(Verb.GET, "https://api.pinterest.com/v1/boards/" + pr.board + "/pins?limit=100&fields=id,link,url,creator,created_at,note,color,counts,media,attribution,image,metadata&access_token=" + token.getAccessToken)
            client.signRequest(token, request)
            val response = client.execute(request)
            if (response.getCode == 200) {
                val json = (Json.parse(response.getBody) \ "data").as[List[JsObject]]
                // Send back to our parent
                parent ! new PinterestObjects(json)
            }
        }
    }
}

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
    var pollerActor: ActorRef = _
    
    override def _receive = {
        case sp: StopPacket => {
            if (pollerActor != null) pollerActor ! Broadcast(StopPacket)
            cleanup
        }
        case config: JsValue => {
            // Get key and secret
            val key = (config \ "key").as[String]
            val secret = (config \ "secret").as[String]
            // Same for token
            val aToken = (config \ "token").as[String]
            // Get board to track
            val boards = (config \ "boards").as[List[String]]
            
            // Set up client
            val service = new ServiceBuilder()
                .apiKey(key)
                .apiSecret(secret)
                .scope("read_public")
                .build(PinterestApi.instance)
            // Token
            val token = new OAuth2AccessToken(aToken, "")
            
            // Get start and end time
            val interval = (config \ "interval").asOpt[JsObject]
            val (startTime: Long, endTime: Option[Long]) = interval match {
                case Some(intvl) => {
                    ((intvl \ "start").asOpt[Long].getOrElse(System.currentTimeMillis / 1000L),
                        (intvl \ "end").asOpt[Long])
                }
                case None => (System.currentTimeMillis / 1000L, None)
            }
            
            // Set up actors
            pollerActor = Akka.system.actorOf(RoundRobinPool(boards.size)
                .props(Props(classOf[AsyncPinterestActor], self, service, token, startTime, endTime))
            )
            boards.foreach{board =>
                pollerActor ! new PinterestRequest(board, null)
            }
        }
        case po: PinterestObjects => po.data.foreach(datum => channel.push(new DataPacket(List(Map(resultName -> datum)))))
    }
}