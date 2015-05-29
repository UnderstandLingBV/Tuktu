package tuktu.social.generators

import tuktu.api.BaseGenerator
import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import twitter4j.TwitterStreamFactory
import twitter4j.FilterQuery
import twitter4j.conf.ConfigurationBuilder
import akka.actor.Props
import org.scribe.builder.api.TwitterApi
import org.scribe.builder.ServiceBuilder
import org.scribe.model.Token
import play.api.libs.concurrent.Akka
import play.api.Play.current
import org.scribe.oauth.OAuthService
import akka.actor.ActorLogging
import akka.actor.Actor
import org.scribe.model.Verb
import org.scribe.model.OAuthRequest
import play.api.libs.json.Json
import java.text.SimpleDateFormat
import akka.actor.PoisonPill
import java.math.BigInteger

case class SearchPacket(
        keywords: List[String],
        maxId: Option[BigInteger]
)
case class ReplyPacket(
        tweet: JsObject
)

class SearchProcessor(actorId: Int, client: OAuthService, aToken: Token, parentActor: ActorRef) extends Actor with ActorLogging {
    def receive() = {
        case sp: SearchPacket => {
            // Make the request
            val request = new OAuthRequest(Verb.GET, "https://api.twitter.com/1.1/search/tweets.json")
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
                .provider(classOf[TwitterApi])
                .apiKey(consumerKey)
                .apiSecret(consumerSecret)
                .callback("http://localhost")
                .build()
            val aToken = new Token(accessToken, accessTokenSecret)
            
            val actor = Akka.system.actorOf(Props(new SearchProcessor(0, sService, aToken, self)))
            
            actor ! new SearchPacket(keywords, None)
        }
        case rp: ReplyPacket => channel.push(new DataPacket(List(Map(resultName -> rp.tweet))))
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}