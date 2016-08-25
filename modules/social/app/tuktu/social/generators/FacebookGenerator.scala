package tuktu.social.generators

import scala.collection.JavaConversions.asScalaBuffer
import scala.concurrent.duration.DurationLong

import org.joda.time.format.DateTimeFormat

import com.fasterxml.jackson.databind.JsonNode
import com.googlecode.batchfb.FacebookBatcher

import akka.actor._
import tuktu.api._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json

case class FBDataRequest(
    urls: List[String],
    start: Option[Long],
    end: Option[Long]
)

class AsyncFacebookCollector(parentActor: ActorRef, fbClient: FacebookBatcher, updateTime: Long) extends Actor with ActorLogging {
    val fbTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ")

    def receive() = {
        case fbdr: FBDataRequest => {
            val urls = fbdr.urls
            val now = System.currentTimeMillis / 1000
            // Get start and end time
            val startTime = fbdr.start match {
                case Some(st) => st
                case None     => 0L
            }
            val endTime = fbdr.end match {
                case Some(en) => en
                case None     => 13592062088L // Some incredibly large number
            }

            // Get the URLs in batched fasion
            val searches = (for (url <- urls) yield fbClient.paged({
                url + {
                    if (fbdr.start != None) "&since=" + fbdr.start.getOrElse(0L).toString else ""
                } + {
                    if (fbdr.end != None) "&until=" + fbdr.end.getOrElse(13592062088L).toString else ""
                }
            }, classOf[JsonNode])).toList
            // Go through them
            val newUrls = (for ((search, index) <- searches.zipWithIndex) yield {
                var resultsFound = false
                var currentSearch = search.get
                var numResults = 0
                do {
                    if (currentSearch != null) {
                        // Get the individual JSON results
                        val objects = currentSearch.toList
                        if (objects.nonEmpty) resultsFound = true
                        for (obj <- objects) {
                            val playJs = Json.parse(obj.toString)
                            // Get the unix timestamp of this object
                            val timestamp = (playJs \ "created_time").as[String]
                            val unixTime = fbTimeFormat.parseDateTime(timestamp).getMillis() / 1000
                            // See if the creation time was within our interval
                            if (unixTime <= endTime && unixTime >= startTime) {
                                // Send result to parent
                                parentActor ! new ResponsePacket(playJs)
                                numResults += 1
                            }
                        }

                        // Get next
                        if (search.next != null)
                            currentSearch = search.next.get
                        else currentSearch = null
                    }
                } while (currentSearch != null && numResults == 50)

                // We are done with this backtracking, see if we need to do more for this search
                if (resultsFound) {
                    // Construct the url
                    urls(index)
                } else ""
            }).toList.filter(elem => elem != "")

            // Schedule next request, if applicable
            // TODO: Maybe simplify this
            (fbdr.start, fbdr.end) match {
                case (start: Option[Long], end: Option[Long]) if start != None && end != None => now match {
                    case n if n < start.getOrElse(0L) => {
                        // Start-time is yet to come, schedule next polling
                        Akka.system.scheduler.scheduleOnce(updateTime seconds, self,
                            new FBDataRequest(newUrls, fbdr.start, fbdr.end))
                    }
                    case n if (n >= start.getOrElse(0L) && n <= end.getOrElse(13592062088L)) => {
                        // End time is still in the future, so we start from where we left off (which is 'now')
                        Akka.system.scheduler.scheduleOnce(updateTime seconds, self,
                            new FBDataRequest(newUrls, Some(now), fbdr.end))
                    }
                    case n if n > end.getOrElse(13592062088L) => {
                        // Stop, end-time is already in the past
                        parentActor ! new StopPacket
                        self ! PoisonPill
                    }
                }
                case (start: Option[Long], end: Option[Long]) if start != None && end == None => now match {
                    case n if n < start.getOrElse(0L) => {
                        // Start-time is yet to come, schedule next one
                        Akka.system.scheduler.scheduleOnce(updateTime seconds, self,
                            new FBDataRequest(newUrls, fbdr.start, fbdr.end))
                    }
                    case n if n >= start.getOrElse(0L) => {
                        // Start time was in the past, but there is no end-time, we have to keep on going perpetually
                        Akka.system.scheduler.scheduleOnce(updateTime seconds, self,
                            new FBDataRequest(newUrls, Some(now), fbdr.end))
                    }
                }
                case (start: Option[Long], end: Option[Long]) if start == None && end != None => now match {
                    case n if n <= end.getOrElse(13592062088L) => {
                        // End time is still in the future, so we start from where we left off (which is 'now')
                        Akka.system.scheduler.scheduleOnce(updateTime seconds, self,
                            new FBDataRequest(newUrls, Some(now), fbdr.end))
                    }
                    case n if n > end.getOrElse(13592062088L) => {
                        // Stop, end-time is already in the past
                        parentActor ! new StopPacket
                    }
                }
                case _ => {
                    // Default, get next one
                    Akka.system.scheduler.scheduleOnce(updateTime seconds, self,
                        new FBDataRequest(newUrls, fbdr.start, fbdr.end))
                }
            }
        }
    }
}

class FacebookGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get credentials
            val credentials = (config \ "credentials").as[JsObject]
            val aToken = (credentials \ "access_token").as[String]
            // Get update time
            val updateTime = (config \ "update_time").asOpt[Long].getOrElse(5L)

            // Set up Scribe client
            val fbClient = new FacebookBatcher(aToken)

            // Filters that we need to check
            val filters = Common.getFilters(config)
            val keywords = filters("keywords").asInstanceOf[Array[String]]
            val users = filters("userids").asInstanceOf[Array[String]]

            // Check period, if given
            val interval = (config \ "interval").asOpt[JsObject]
            var (startTime: Option[Long], endTime: Option[Long]) = interval match {
                case Some(intvl) => {
                    ((intvl \ "start").asOpt[Long],
                        (intvl \ "end").asOpt[Long])
                }
                case None => (None, None)
            }

            /**
             * See what we need to do.
             * - If only a start-time is given, we need to perpetually fetch from that time on.
             * - If only an end-time is given, we need to fetch everything until that time and then stop. If the end-time
             *       is in the future, we need to continue to watch FB until the end-time passes.
             * - If both are given we need to fetch until the end-time but not go back beyond the start-time.
             * - If none are given, we start to perpetually fetch from now on.
             */
            val now = System.currentTimeMillis / 1000
            if (startTime == null && endTime == null) startTime = Some(now)

            // Set up the initial URLs
            val userUrls = {
                if (users != null) {
                    (for (userid <- users) yield {
                        userid + "/feed?limit=50"
                    }).toList
                } else List()
            }
            val keywordUrls = {
                if (keywords != null) {
                    (for (keyword <- keywords) yield {
                        "/search?q=" + keyword + "&type=post&limit=50"
                    }).toList
                } else List()
            }

            // Merge URLs and send to periodic actor
            val urls = userUrls ++ keywordUrls
            val pollerActor = Akka.system.actorOf(Props(classOf[AsyncFacebookCollector], self, fbClient, updateTime))
            pollerActor ! new FBDataRequest(urls, startTime, endTime)
        }
        case data: ResponsePacket => channel.push(new DataPacket(List(Map(resultName -> data.json))))
        case sp: StopPacket       => cleanup
        case ip: InitPacket       => setup
    }
}