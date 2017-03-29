package tuktu.social.generators

import scala.collection.JavaConversions.asScalaBuffer
import scala.concurrent.duration.DurationLong

import org.joda.time.format.DateTimeFormat

import akka.actor._
import tuktu.api._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import com.restfb.DefaultFacebookClient
import com.restfb.Version
import com.fasterxml.jackson.databind.JsonNode
import com.restfb.batch.BatchRequest.BatchRequestBuilder
import com.restfb.Parameter

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.restfb.types.Post
import com.restfb.Connection
import com.restfb.json.JsonObject
import scala.concurrent.Future
import akka.pattern.ask
import scala.concurrent.duration.DurationInt
import play.api.cache.Cache
import akka.util.Timeout
import java.text.SimpleDateFormat
import java.util.Locale
import play.api.libs.json.JsResultException
import play.api.Logger

case class FBDataRequest(
    urls: List[String],
    start: Option[Long],
    end: Option[Long]
)

case class FBCommentDataRequest(
    posts: Map[String, JsObject]
)

case class FBIntrospect(
    data: JsObject,
    from: String
)

case class PostRequest(
    posts: List[FBIntrospect]
)

case class CommentRequest(
    comments: List[FBIntrospect]
)

case class FlushAuthors()
case class CommentProcessing()

/**
 * Gets all posts from a facebook page
 */
class AsyncFacebookCollector(parentActor: ActorRef, fbClient: DefaultFacebookClient, updateTime: Long, fields: String, getComments: Boolean, runOnce: Boolean, flushInterval: Int, commentInterval: Int, commentFrequency: Int) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    // Set up the author fetching actor
    val authorActor = Akka.system.actorOf(Props(classOf[UserCollector], parentActor, fbClient))
    // If we need to get the comments too, make sure we set up the actor
    val commentsActor = if (getComments) Some(Akka.system.actorOf(Props(classOf[CommentsCollector], parentActor, fbClient, authorActor))) else None

    // Also keep track of all posts obtained so far, so we can get author stuff properly
    val posts = collection.mutable.ListBuffer.empty[FBIntrospect]
    
    // Keep track of posts that have been processed so we can get the comments after a while
    val processedPosts = collection.mutable.Map.empty[String, JsObject]
    
    // When it takes too long to send posts to fetch the authors
    var cancellablePosts: Cancellable = context.system.scheduler.scheduleOnce(flushInterval seconds, self, new FlushAuthors)
    
    // We start fetching comments every comment interval
    if (commentsActor != None)
        context.system.scheduler.schedule(commentInterval seconds, commentInterval seconds, self, new CommentProcessing)
    
    def processPosts() {
        posts.foreach(post => processedPosts += (post.data \ "id").as[String] -> post.data)
        posts.clear
    }
    
    def receive() = {
        case cp: CommentProcessing => {
            val tooOldTime = System.currentTimeMillis - commentInterval * 1000
            // Process all the comments that are 'old' -> find everything that is older than commentInterval
            val eligiblePosts = processedPosts.filter { post =>
                val id = post._1
                val json = post._2
                // Get the timestamp
                val time = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
                    .parse((json \ "created_time").as[String]).getTime
                // See if it is too old
                time < tooOldTime
            }
            
            // Forward them
            commentsActor.get ! new FBCommentDataRequest(eligiblePosts map {p =>
                p._1 -> (p._2 - "tuktu_fetch_count")
            } toMap)
            
            // Update posts to repeat fetching comments
            eligiblePosts.foreach {post =>
                // Check if this one contains our counter
                val newPost = (post._2 \ "tuktu_fetch_count").asOpt[Int] match {
                    case None => post._2 ++ Json.obj("tuktu_fetch_count" -> 1)
                    case Some(fc) => post._2 ++ Json.obj("tuktu_fetch_count" -> (fc + 1))
                }
                // Check if it's within the boundary
                if ((newPost \ "tuktu_fetch_count").as[Int] >= commentFrequency)
                    processedPosts -= post._1
                else processedPosts += post._1 -> newPost
            }
        }
        case fa: FlushAuthors => {
            // Check if there is even data
            if (posts.size > 0) {
                // Send to the author actor
                authorActor ! new PostRequest(posts.toList)
                // Add the posts to our processed list
                processPosts
            }
            
            context.system.scheduler.scheduleOnce(flushInterval seconds, self, new FlushAuthors)
        }
        case sp: StopPacket => {
            commentsActor match {
                case Some(ca) => {
                    if (processedPosts.size > 0)
                        commentsActor.get ! new FBCommentDataRequest(processedPosts toMap)
                    if (posts.size > 0)
                        authorActor ! new PostRequest(posts.toList)
                    Future.sequence(List(ca ? sp, authorActor ? sp)).onComplete { case a => self ! PoisonPill }
                }
                case None => {}
            }
        }
        case fbdr: FBDataRequest => {
            val urls = fbdr.urls
            val now = System.currentTimeMillis / 1000
            // Get start and end time
            val startTime = fbdr.start match {
                case Some(st) => st
                case None     => System.currentTimeMillis / 1000
            }
            val endTime = fbdr.end match {
                case Some(en) => en
                case None     => 13592062088L // Some incredibly large number
            }

            // Get the URLs in batched fasion
            val requests = for (url <- urls) yield {
                // Build the start and end parameters
                val parameters = Array(Parameter.`with`("limit", 50), Parameter.`with`("fields", fields)) ++ {
                        fbdr.start match {
                            case Some(s) => Array(Parameter.`with`("since", s))
                            case None => Array[Parameter]()
                        }
                    } ++ {
                        fbdr.end match {
                            case Some(e) => Array(Parameter.`with`("until", e))
                            case None => Array[Parameter]()
                        }
                    }
                    
                // Add the batched request
                new BatchRequestBuilder(url)
                        .parameters(parameters: _*)
                        .build()
            }
            
            // Make the requests
            val responses = fbClient.executeBatch(requests.asJava)
            
            for ((response, index) <- responses.zipWithIndex) {
                val objectList = new Connection[JsonObject](fbClient, response.getBody, classOf[JsonObject])
                for (objects <- objectList) {
                    for (obj <- objects) {
                        // Get the post
                        val post = fbClient.getJsonMapper.toJavaObject(obj.toString, classOf[Post])
                        
                        // Parse time and all that
                        val unixTime = post.getCreatedTime.getTime / 1000
                        // See if the creation time was within our interval
                        if (unixTime <= endTime && unixTime >= startTime) {
                                // Add to our buffer
                                posts += new FBIntrospect(
                                        Json.parse(obj.toString).asInstanceOf[JsObject],
                                        post.getFrom.getId
                                )
                                
                                // Cancel the time flushing actor and reset
                                cancellablePosts.cancel
                                cancellablePosts = context.system.scheduler.scheduleOnce(flushInterval seconds, self, new FlushAuthors)
                                
                                if (posts.size == 50) {
                                    // Get the from/to fields using our user collector actor
                                    authorActor ! new PostRequest(posts.toList)
                                    processPosts
                                }
                        }
                    }
                }
            }
            
            // Schedule next request, if applicable
            if (!runOnce) {
                now match {
                    case n if n < fbdr.start.getOrElse(0L) => {
                        // Start-time is yet to come, schedule next polling
                        Akka.system.scheduler.scheduleOnce(updateTime seconds, self,
                            new FBDataRequest(urls, fbdr.start, fbdr.end))
                    }
                    case n if (n >= fbdr.start.getOrElse(0L) && n <= fbdr.end.getOrElse(13592062088L)) => {
                        // End time is still in the future, so we start from where we left off (which is 'now')
                        Akka.system.scheduler.scheduleOnce(updateTime seconds, self,
                            new FBDataRequest(urls, Some(now), fbdr.end))
                    }
                    case n if n > fbdr.end.getOrElse(13592062088L) =>
                        // Stop, end-time is already in the past
                        parentActor ! new StopPacket
                }
            } else parentActor ! new StopPacket
        }
    }
}

/**
 * Gets a full user profile given an object (post/comment)
 */
class UserCollector(parentActor: ActorRef, fbClient: DefaultFacebookClient) extends Actor with ActorLogging {
    /*
     * Not all fields can just be obtained using public tokens. Here we keep the mapping of those fields
     * that we can actually get per node type
     */
    val eligibleFields = Map(
        "page" -> List("id", "about", "affiliation", "artists_we_like", "attire", "awards", "band_interests", "band_members", "best_page", "bio", "birthday", "booking_agent", "built", "business", "can_checkin", "can_post", "category", "category_list", "checkins", "company_overview", "contact_address", "country_page_likes", "cover", "culinary_team", "current_location", "description", "description_html", "directed_by", "display_subtext", "emails", "fan_count", "features", "food_styles", "founded", "general_info", "general_manager", "genre", "global_brand_page_name", "global_brand_root_id", "has_added_app", "hometown", "hours", "influences", "is_community_page", "is_permanently_closed", "is_published", "is_unclaimed", "is_verified", "leadgen_tos_accepted", "link", "location", "members", "mission", "mpg", "name", "network", "new_like_count", "offer_eligible", "overall_star_rating", "parent_page", "parking", "payment_options", "personal_info", "personal_interests", "pharma_safety_info", "phone", "place_type", "plot_outline", "press_contact", "price_range", "produced_by", "products", "promotion_ineligible_reason", "public_transit", "publisher_space", "rating_count", "record_label", "release_date", "restaurant_services", "restaurant_specialties", "schedule", "screenplay_by", "season", "single_line_address", "starring", "store_number", "studio", "talking_about_count", "unread_message_count", "unread_notif_count", "unseen_message_count", "username", "voip_info", "website", "were_here_count", "written_by"),
        "user" -> List("id", "about", "age_range", "birthday", "cover", "currency", "devices", "education", "email", "favorite_athletes", "favorite_teams", "first_name", "gender", "hometown", "inspirational_people", "install_type", "installed", "interested_in", "is_verified", "languages", "last_name", "link", "locale", "location", "meeting_for", "middle_name", "name", "name_format", "payment_pricepoints", "political", "public_key", "quotes", "relationship_status", "religion", "security_settings", "significant_other", "sports", "third_party_id", "timezone", "updated_time", "verified", "video_upload_limits", "viewer_can_send_gift", "website", "work")
    )
    
    def receive() = {
        case sp: StopPacket => {
            sender ! "ok"
            self ! PoisonPill
        }
        case comments: CommentRequest => getProfiles(comments.comments, true)
        case posts: PostRequest => getProfiles(posts.posts, false)
    }
    
    def getProfiles(objs: List[FBIntrospect], isComment: Boolean) = {
        // We don't know the type, so are constrained to using metadata to figure it out
        val requestList = objs.map(obj => {
            // Add the batched request for from
            new BatchRequestBuilder(obj.from)
                    .parameters(Parameter.`with`("metadata", 1))
                    .build()
        })
        
        // Make the requests, per 50
        requestList.grouped(50).foreach(requests => {
            val responses = fbClient.executeBatch(requests.asJava)
        
            // Using the metadata, make subsequent requests for the specific page types
            val profileRequests = responses.map(response => {
                val json = Json.parse(response.getBody)
                // Get the type
                val nodeType = try {
                    (json \ "metadata" \ "type").as[String]
                } catch {
                    case e: JsResultException => {
                        Logger.warn("Could not fetch node type for Facebook profile: " + json)
                        "Unknown"
                    }
                }
                // Get all the eligible fields
                val fields = if (nodeType != "Unknown") eligibleFields(nodeType) else Nil
                
                // Make new request with all the fields we can get
                (nodeType, new BatchRequestBuilder((json \ "id").as[String])
                    .parameters(Parameter.`with`("fields", fields.mkString(",")))
                    .build())
            }).filter(_._1 != "Unknown")
            
            // Get all the real profile data
            val profileResponses = fbClient.executeBatch(profileRequests.map(_._2).asJava)
        
            profileResponses.zipWithIndex.zip(objs.map(_.data)).foreach(el => el match {
                case (r, obj) => r match {
                    case (response, index) => {
                        // Get the type
                        val nodeType = profileRequests(index)._1
                        
                        // Fill in the blanks
                        val json = Json.parse(response.getBody).as[JsObject]
                        val newObject = (obj ++ Json.obj("is_comment" -> isComment)).deepMerge(
                                Json.obj("from" -> (json ++ Json.obj("type" -> nodeType)))
                        )
                        
                        // Send to our parent
                        parentActor ! new ResponsePacket(newObject)
                    }
                }
            })
        })
    }
}

/**
 * Collects comments of posts
 */
class CommentsCollector(parentActor: ActorRef, fbClient: DefaultFacebookClient, userActor: ActorRef) extends Actor with ActorLogging {
    def receive() = {
        case sp: StopPacket => {
            sender ! "ok"
            self ! PoisonPill
        }
        case fdr: FBCommentDataRequest => {
            // Make sure we get all the comments of all the post IDs given
            val ids = fdr.posts.keys toList
            val urls = ids.map(_ + "/comments")
            
            if (urls.size > 0) {
                // Get the URLs in batched fashion
                val rs = (for (url <- urls) yield {
                    // Build the start and end parameters
                    val parameters = Array(Parameter.`with`("limit", 50), Parameter.`with`("fields", "id,attachment,comment_count,created_time,from,like_count,message,message_tags,object,parent,user_likes,likes.limit(0).summary(true),comments.limit(0).summary(true),shares"))
                        
                    // Add the batched request
                    new BatchRequestBuilder(url)
                            .parameters(parameters: _*)
                            .build()
                }).grouped(50)
                
                rs.foreach {requests =>
                    // Make the requests
                    val responses = fbClient.executeBatch(requests.asJava)
                    
                    var offset = 0
                    userActor ! new CommentRequest(responses.flatMap(response => {
                        // Use try since sometimes comments are removed before we see them
                        val res = try {
                            val objectList = new Connection[JsonObject](fbClient, response.getBody, classOf[JsonObject])
                            objectList.flatMap(objects => {
                                objects.map(obj => {
                                    val json = Json.parse(obj.toString).asInstanceOf[JsObject]
                                    new FBIntrospect(
                                            // Merge the original post into the comment
                                            json ++ Json.obj("post" -> fdr.posts(ids(offset))),
                                            (json \ "from" \ "id").as[String]
                                    )
                                })
                            })
                        } catch {
                            case e: com.restfb.json.JsonException => List()
                        }
                        
                        // Increase the offset
                        offset += 1
                        
                        res
                    }).toList)
                }
            }
        }
    }
}

class FacebookGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    val allFields = List(
        "id", "application", "call_to_action", "caption", "child_attachments", "comments_mirroring_domain", "coordinates", "created_time", "description", "event", "expanded_height", "expanded_width", "feed_targeting", "full_picture", "height", "icon", "instagram_eligibility", "is_expired", "is_hidden", "is_instagram_eligible", "is_popular", "is_published", "is_spherical", "link", "message", "message_tags", "name", "object_id", "parent_id", "permalink_url", "picture", "place", "privacy", "promotion_status", "properties", "scheduled_publish_time", "shares", "source", "status_type", "story", "story_tags", "target", "targeting", "timeline_visibility", "type", "updated_time", "via", "width", "likes.limit(0).summary(true)", "comments.limit(0).summary(true)", "from", "to"
    )
    
    var pollerActor: ActorRef = _
    
    override def _receive = {
        case sp: StopPacket => {
            pollerActor ! new StopPacket
            cleanup
        }
        case config: JsValue => {
            // Get credentials
            val credentials = (config \ "credentials").as[JsObject]
            val aToken = (credentials \ "access_token").as[String]
            // Get update time
            val updateTime = (config \ "update_time").asOpt[Long].getOrElse(5L)
            // Get the fields we need
            val fields = (config \ "fields").asOpt[List[String]].getOrElse(allFields).mkString(",")
            // See if we need to get comments
            val getComments = (config \ "get_comments").asOpt[Boolean].getOrElse(false)
            // Since we get all data back to start time in one go, we might just stop there
            val runOnce = (config \ "run_once").asOpt[Boolean].getOrElse(false)
            
            // Get flush and comment intervals
            val flushInterval = (config \ "flush_interval").asOpt[Int].getOrElse(60)
            val commentInterval = (config \ "comment_interval").asOpt[Int].getOrElse(3600)
            val commentFrequency = (config \ "comment_frequency").asOpt[Int].getOrElse(5)
            
            // Set up RestFB
            val fbClient = new DefaultFacebookClient(aToken, Version.VERSION_2_8)

            // Filters that we need to check
            val filters = Common.getFilters(config)
            val users = filters("userids")
                .asInstanceOf[Array[String]].map(_ + "/feed")
                
            // Stop if there are no users
            if (users.size == 0) self ! new StopPacket
            else {
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
                if (startTime == None && endTime == None) startTime = Some(now)
    
                // Merge URLs and send to periodic actor
                pollerActor = Akka.system.actorOf(Props(classOf[AsyncFacebookCollector], self, fbClient, updateTime, fields, getComments, runOnce, flushInterval, commentInterval, commentFrequency))
                pollerActor ! new FBDataRequest(users.toList, startTime, endTime)
            }
        }
        case data: ResponsePacket => channel.push(DataPacket(List(Map(resultName -> data.json))))
    }
}