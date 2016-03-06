package tuktu.social.generators

import java.math.BigInteger

import com.github.scribejava.apis.TwitterApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth10aService

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket

case class SearchPacket(
        keywords: List[String],
        maxId: Option[BigInteger]
)
case class ReplyPacket(
        tweet: JsObject
)

class SearchProcessor(actorId: Int, client: OAuth10aService, aToken: OAuth1AccessToken, parentActor: ActorRef) extends Actor with ActorLogging {
    def receive() = {
        case sp: SearchPacket => {
            // Make the request
            val request = new OAuthRequest(Verb.GET, "https://api.twitter.com/1.1/search/tweets.json", client)
            request.addQuerystringParameter("q", sp.keywords.map(keyword => {
                if (keyword.contains(" ")) "(" + keyword.replaceAll(" ", " AND ") + ")"
                else keyword
            }).mkString(" OR "))
            
            sp.maxId match {
                case None => {}
                case Some(id) => request.addQuerystringParameter("max_id", id.toString)
            }
            client.signRequest(aToken, request)
            val response = request.send
            
            // Check for success
            if (response.isSuccessful) {
                // Get the tweets
                val json = Json.parse(response.getBody)
                val tweets = (json \ "statuses").as[List[JsObject]]
                
                // Keep track of until
                var minId: BigInteger = sp.maxId.getOrElse(new BigInteger("9999999999999999999999"))
                tweets.foreach(tweet => {
                    // Make a datapacket of it
                    parentActor ! new ReplyPacket(tweet)
                    
                    // Get the id
                    val id = new BigInteger((tweet \ "id_str").as[String])

                    // See if this ID is less
                    if (id.compareTo(minId) == -1) minId = id
                })
                
                // Make new request if minId didn't change
                if (
                        minId.compareTo(sp.maxId.getOrElse(new BigInteger("9999999999999999999999"))) == -1
                ) {
                    self ! new SearchPacket(sp.keywords, Some(minId))
                } else {
                    parentActor ! new StopPacket()
                    self ! PoisonPill
                }
            } else {
                parentActor ! new StopPacket()
                self ! PoisonPill
            }
        }
    }
}

class TwitterSearchGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get all credentials and set up scribe actors
             val credentials = (config \ "credentials").as[JsObject]
             
             // Credentials
             val consumerKey = (credentials \ "consumer_key").as[String]
             val consumerSecret = (credentials \ "consumer_secret").as[String]
             val accessToken = (credentials \ "access_token").as[String]
             val accessTokenSecret = (credentials \ "access_token_secret").as[String]
             
             // Filter
             val keywords = (config \ "filters" \ "keywords").as[List[String]]
             
             // Build scribe service
            val sService = new ServiceBuilder()
                .apiKey(consumerKey)
                .apiSecret(consumerSecret)
                .callback("http://localhost")
                .build(TwitterApi.instance())
            val aToken = new OAuth1AccessToken(accessToken, accessTokenSecret)
            
            val actor = Akka.system.actorOf(Props(new SearchProcessor(0, sService, aToken, self)))
            
            actor ! new SearchPacket(keywords, None)
        }
        case rp: ReplyPacket => channel.push(new DataPacket(List(Map(resultName -> rp.tweet))))
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}