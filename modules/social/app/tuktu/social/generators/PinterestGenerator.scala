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
import org.joda.time.format.DateTimeFormat

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

case class PinterestRequest(
        board: String,
        cursor: String,
        attempt: Int,
        afterTime: Long,
        afterBackTrack: Long
)
case class PinterestObjects(
        data: List[JsObject]
)
case class PinterestDone()

class AsyncPinterestActor(parent: ActorRef, client: OAuth20Service, token: OAuth2AccessToken, start: Long, end: Option[Long], maxAttempts: Int, updateTime: Int) extends Actor with ActorLogging {
    val timeformat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")
    
    def receive() = {
        case pr: PinterestRequest => {
            // Search board
            val request = new OAuthRequest(Verb.GET, "https://api.pinterest.com/v1/boards/" + pr.board + "/pins?" + {
                if (pr.cursor != null) "cursor=" + pr.cursor + "&" else ""
            } + "limit=100&fields=id,link,url,creator,created_at,note,color,counts,media,attribution,image,metadata&access_token=" + token.getAccessToken)
            client.signRequest(token, request)
            val response = client.execute(request)
            if (response.getCode == 200) {
                val json = Json.parse(response.getBody)
                val data = (json \ "data").as[List[JsObject]]
                
                val (sentData, oldest, newest, afterBackTrack) = if (data.size > 0) {
                    // Send the data back to our parent
                    val sd = data.filter{pin =>
                        val t = timeformat.parseDateTime((pin \ "created_at").as[String]).getMillis / 1000L
                        t > pr.afterTime && {
                            if (pr.cursor == null) t > pr.afterBackTrack else true
                        }
                    }
                    parent ! new PinterestObjects(sd)
                    
                    // Find the oldest and newest pin in this request
                    val o = timeformat.parseDateTime((data.minBy{pin =>
                        timeformat.parseDateTime((pin \ "created_at").as[String]).getMillis
                    } \ "created_at").as[String]).getMillis / 1000L
                    val n = timeformat.parseDateTime((data.maxBy{pin =>
                        timeformat.parseDateTime((pin \ "created_at").as[String]).getMillis
                    } \ "created_at").as[String]).getMillis / 1000L
                            
                    (sd, o, n, if (pr.cursor == null) n else pr.afterBackTrack)
                } else (Nil, 0L, pr.afterTime, pr.afterBackTrack)
                //  Get the cursor
                val cursor = (json \ "page" \ "cursor").as[String]
                
                // Check if we still need to collect more
                if (sentData.size > 0 && oldest > start)
                    // Start the back tracking of older pins
                    self ! new PinterestRequest(pr.board, cursor, 0, pr.afterTime, afterBackTrack)
                else {
                    // We do not need to fetch older ones, see if we need to schedule fetching new ones via polling
                    val poll = end match {
                        case None => true
                        case Some(e) if newest < e => true
                        case _ => false
                    }
                    if (poll)
                        // Schedule next request
                        Akka.system.scheduler.scheduleOnce(updateTime seconds, self,
                            new PinterestRequest(pr.board, null, 0, newest, pr.afterBackTrack))
                    else
                        parent ! new PinterestDone
                }
            } else if (pr.attempt == maxAttempts - 1)
                // We have an error and retried too much
                parent ! new PinterestDone
            else
                // We can go again
                self ! new PinterestRequest(pr.board, pr.cursor, pr.attempt + 1, pr.afterTime, pr.afterBackTrack)
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
    var totalDone = 0
    var actorCount = 0
    
    override def _receive = {
        case sp: StopPacket => {
            if (pollerActor != null) pollerActor ! Broadcast(StopPacket)
            cleanup
        }
        case pd: PinterestDone => {
            totalDone += 1
            if (totalDone == actorCount)
                // All children are done, terminate
                self ! new StopPacket
        }
        case config: JsValue => {
            // Get key and secret
            val key = (config \ "key").as[String]
            val secret = (config \ "secret").as[String]
            // Same for token
            val aToken = (config \ "token").as[String]
            // Get board to track
            val boards = (config \ "boards").as[List[String]]
            actorCount = boards.size
            // Max retries on error
            val maxAttempts = (config \ "max_attempts").asOpt[Int].getOrElse(3)
            // Get update time
            val updateTime = {
                val ut = (config \ "update_time").asOpt[Int].getOrElse(5)
                // Check if our update time is going to hit rate limits (1k calls per hour) - we can only do this for the realtime setting
                if (3600 / ut * boards.size > 1000) Math.ceil(3.6 * boards.size) else ut
            }
            
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
                .props(Props(classOf[AsyncPinterestActor], self, service, token, startTime, endTime, maxAttempts, updateTime))
            )
            boards.foreach{board =>
                pollerActor ! new PinterestRequest(board, null, 0, startTime, 0L)
            }
        }
        case po: PinterestObjects => po.data.foreach(datum => channel.push(new DataPacket(List(Map(resultName -> datum)))))
    }
}