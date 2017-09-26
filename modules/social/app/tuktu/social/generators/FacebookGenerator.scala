package tuktu.social.generators

import tuktu.api.BaseGenerator
import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import tuktu.api.ResponsePacket
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import com.restfb.DefaultFacebookClient
import com.restfb.Version
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.Props
import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.api.libs.iteratee.Concurrent
import com.restfb.batch.BatchRequest.BatchRequestBuilder
import com.restfb.Parameter
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.restfb.json.JsonObject
import com.restfb.Connection
import com.restfb.types.Post
import play.api.libs.json.Json
import play.api.libs.json.JsResultException
import play.api.Logger
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.DurationLong
import akka.actor.Cancellable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.pattern.ask
import akka.actor.PoisonPill

case class PostCollectorPacket(
        feeds: List[String],
        startTime: Long,
        endTime: Option[Long]
)
case class PostList(
        posts: List[JsObject],
        is_comment: Boolean
)
case class IteratePosts()
case class IterateComments()
case class FlushAuthors()

class PostCollector(fbClient: DefaultFacebookClient, commentCollector: ActorRef, authorCollector: ActorRef, flushInterval: Int) extends Actor with ActorLogging {
    val fields = List(
        "id", "application", "call_to_action", "caption", "child_attachments", "comments_mirroring_domain", "coordinates", "created_time", "description", "event", "expanded_height", "expanded_width", "feed_targeting", "full_picture", "height", "icon", "instagram_eligibility", "is_expired", "is_hidden", "is_instagram_eligible", "is_popular", "is_published", "is_spherical", "link", "message", "message_tags", "name", "object_id", "parent_id", "permalink_url", "picture", "place", "privacy", "promotion_status", "properties", "scheduled_publish_time", "shares", "source", "status_type", "story", "story_tags", "target", "targeting", "timeline_visibility", "type", "updated_time", "via", "width", "likes.limit(0).summary(true)", "comments.limit(0).summary(true)", "from", "to"
    ).mkString(",")
    
    val postsBuffer = collection.mutable.ListBuffer.empty[JsObject]
    
    // When it takes too long to send posts to fetch the authors
    var cancellablePosts: Cancellable = context.system.scheduler.scheduleOnce(flushInterval seconds, self, new FlushAuthors)
    
    def receive() = {
        case sp: StopPacket => {
            sender ! "ack"
            self ! PoisonPill
        }
        case fa: FlushAuthors => {
            authorCollector ! new PostList(postsBuffer.toList, false)
            postsBuffer.clear
        }
        case pcp: PostCollectorPacket => {
            // Set up the batched requests
            val requestBatches = (for (feed <- pcp.feeds) yield {
                // Build the start and end parameters
                val parameters = Array(Parameter.`with`("limit", 50), Parameter.`with`("fields", fields),
                        Parameter.`with`("since", pcp.startTime)) ++ {
                        pcp.endTime match {
                            case Some(e) => Array(Parameter.`with`("until", e))
                            case None => Array[Parameter]()
                        }
                    }
                    
                // Add the batched request
                new BatchRequestBuilder(feed)
                        .parameters(parameters: _*)
                        .build()
            }).grouped(50)
            
            requestBatches.foreach {requests =>
                
                // Make the requests
                val responses = fbClient.executeBatch(requests.asJava)
                
                // Go over them
                for ((response, index) <- responses.zipWithIndex) {
                    val objectList = try {
                        new Connection[JsonObject](fbClient, response.getBody, classOf[JsonObject])
                    }
                    catch {
                        case e: com.restfb.json.JsonException => {
                            Logger.error("Failed to get Facebook data for " + pcp.feeds + "\r\n" + response.getBody)
                            throw e
                        }
                    }
                    for {
                        objects <- objectList
                        obj <- objects
                    } {
                        // Get the post
                        val post = fbClient.getJsonMapper.toJavaObject(obj.toString, classOf[Post])
                        
                        // Parse time and all that
                        val unixTime = post.getCreatedTime.getTime / 1000
                        
                        // See if the creation time was within our interval
                        val keep = unixTime >= pcp.startTime && (pcp.endTime match {
                            case Some(e) => unixTime <= e
                            case _ => true
                        })
                        
                        if (keep) {
                            // Add to our buffer
                            postsBuffer += Json.parse(obj.toString).asInstanceOf[JsObject]
                            
                            // Cancel the time flushing actor and reset
                            cancellablePosts.cancel
                            cancellablePosts = context.system.scheduler.scheduleOnce(flushInterval seconds, self, new FlushAuthors)
                            
                            if (postsBuffer.size == 50) {
                                // Forward to our comment and author actor
                                authorCollector ! new PostList(postsBuffer.toList, false)
                                postsBuffer.clear
                            }
                        }
                    }
                }
            }
        }
    }
}

class CommentCollector(fbClient: DefaultFacebookClient, authorCollector: ActorRef, commentFrequency: Int) extends Actor with ActorLogging {
    // Contains posts ánd comments we still need to fetch
    val posts = collection.mutable.Map.empty[JsObject, (Long, Int, Boolean)]
    var isRequesting = false
    
    def receive() = {
        case sp: StopPacket => {
            sender ! "ack"
            self ! PoisonPill
        }
        case pl: PostList => pl.posts.foreach {post =>
            posts += post -> (1L, 0, false)
        }
        case ic: IterateComments => {
            if (!isRequesting) {
                isRequesting = true
                val usePosts = posts.toList
                // Set up all urls
                val urls = usePosts.map {post =>
                    (post._1 \ "id").as[String] + "/comments"
                }
                
                val now = System.currentTimeMillis / 1000L
                // Get the URLs in batched fashion
                val rs = (for ((url, offset) <- urls.zipWithIndex) yield {
                    // Build the start and end parameters
                    val parameters = Array(Parameter.`with`("limit", 50), Parameter.`with`("since", usePosts(offset)._2._1), Parameter.`with`("until", now),
                            Parameter.`with`("fields", "id,attachment,comment_count,created_time,from,like_count,message,message_tags,object,parent,user_likes,likes.limit(0).summary(true),comments.limit(0).summary(true),shares"))
                        
                    // Add the batched request
                    new BatchRequestBuilder(url)
                            .parameters(parameters: _*)
                            .build()
                }).grouped(50)
                
                rs.zipWithIndex.foreach {ri =>
                    val requests = ri._1
                    val offset = ri._2
                    // Make the requests
                    val responses = fbClient.executeBatch(requests.asJava)
                    
                    // Keep track of a the comments and send them intermediately
                    val buffer = collection.mutable.ListBuffer.empty[JsObject]
                    responses.zipWithIndex.foreach(res => {
                        val response = res._1
                        val index = res._2
                        // Use try since sometimes comments are removed before we see them
                        
                        val objectList = try {
                            new Connection[JsonObject](fbClient, response.getBody, classOf[JsonObject])
                        } catch {
                            case e: com.restfb.json.JsonException => null
                        }
                        if (objectList != null) {
                            objectList.foreach(objects => {
                                objects.foreach(obj => {
                                    try {
                                        val json = Json.parse(obj.toString).asInstanceOf[JsObject]
                                        // Merge the original post into the comment
                                        val up = usePosts(offset * 50 + index)
                                        val newObj = json ++ Json.obj({
                                            if (up._2._3) "comment" else "post"
                                        } -> up._1, "is_comment_to_post" -> !up._2._3) 
                                        // Add to our buffer
                                        buffer += newObj
                                        // Also add to the list of posts and comments we still have to fetch comments of
                                        posts += newObj -> (1L, 0, true)
                                        // Send if we have reached 10 pages
                                        if (buffer.size == 500) {
                                            authorCollector ! new PostList(buffer.toList, true)
                                            buffer.clear
                                        }
                                    } catch {
                                        case e: com.restfb.json.JsonException => {}
                                    }
                                })
                            })
                        }
                    })
                    
                    // Send residue if any
                    if (buffer.size > 0)
                        authorCollector ! new PostList(buffer.toList, true)
                }
                
                // Update the counts and kick out the ones we don't need anymore
                usePosts.foreach {post =>
                    posts(post._1) = (now + 1, post._2._2 + 1, post._2._3)
                    if (posts(post._1)._2 >= commentFrequency) posts -= post._1
                }
                isRequesting = false
            }
        }
    }
}

class AuthorCollector(fbClient: DefaultFacebookClient, channel: Concurrent.Channel[DataPacket], resultName: String) extends Actor with ActorLogging {
    /*
     * Not all fields can just be obtained using public tokens. Here we keep the mapping of those fields
     * that we can actually get per node type
     */
    val eligibleFields = Map(
        "page" -> List("id","picture","about","affiliation","artists_we_like","attire","awards","band_interests","band_members","best_page","bio","birthday","booking_agent","built","can_checkin","can_post","category","category_list","checkins","company_overview","contact_address","country_page_likes","cover","culinary_team","current_location","description","description_html","directed_by","display_subtext","emails","fan_count","features","food_styles","founded","general_info","general_manager","genre","global_brand_page_name","global_brand_root_id","has_added_app","hometown","hours","influences","is_community_page","is_permanently_closed","is_published","is_unclaimed","is_verified","leadgen_tos_accepted","link","location","members","mission","mpg","name","network","new_like_count","offer_eligible","overall_star_rating","parent_page","parking","payment_options","personal_info","personal_interests","pharma_safety_info","phone","place_type","plot_outline","press_contact","price_range","produced_by","products","promotion_ineligible_reason","public_transit","publisher_space","rating_count","record_label","release_date","restaurant_services","restaurant_specialties","schedule","screenplay_by","season","single_line_address","starring","store_number","studio","talking_about_count","unread_message_count","unread_notif_count","unseen_message_count","username","voip_info","website","were_here_count","written_by"),
        "user" -> List("id","picture","about","age_range","birthday","cover","currency","devices","education","email","favorite_athletes","favorite_teams","first_name","gender","hometown","inspirational_people","install_type","installed","interested_in","is_verified","languages","last_name","link","locale","location","meeting_for","middle_name","name","name_format","payment_pricepoints","political","public_key","quotes","relationship_status","religion","security_settings","significant_other","sports","third_party_id","timezone","updated_time","verified","video_upload_limits","viewer_can_send_gift","website","work")
    )
    var commentCollector: ActorRef = _
    
    def receive() = {
        case sp: StopPacket => {
            sender ! "ack"
            self ! PoisonPill
        }
        case a: ActorRef => commentCollector = a
        case pr: PostList => {
            val usePosts = pr.posts.toList
            // We don't know the type, so are constrained to using metadata to figure it out
            val requestLists = (pr.posts.map {post =>
                // Add the batched request for from
                new BatchRequestBuilder((post \ "from" \ "id").as[String])
                        .parameters(Parameter.`with`("metadata", 1))
                        .build()
            }).grouped(50)
            
            // Make the requests, per 50
            requestLists.zipWithIndex.foreach(rs => {
                val requests = rs._1
                val offset = rs._2
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
                    if (nodeType != "Unknown")
                        (nodeType, new BatchRequestBuilder((json \ "id").as[String])
                            .parameters(Parameter.`with`("fields", eligibleFields(nodeType).mkString(",")))
                            .build())
                    else ("Unknown", null)
                }).filter(_._1 != "Unknown")
                
                // Get all the real profile data
                val profileResponses = fbClient.executeBatch(profileRequests.map(_._2).asJava)
            
                // Check what we need to do with this list of profiles
                val resultList = profileResponses.zipWithIndex.map(el => el match {
                    case (response, index) => {
                        // Get the type
                        val nodeType = profileRequests(index)._1
                        
                        // Fill in the blanks
                        val json = Json.parse(response.getBody).as[JsObject]
                        val newObject = (usePosts(offset * 50 + index) ++ Json.obj("is_comment" -> pr.is_comment)).deepMerge(
                                Json.obj("from" -> (json ++ Json.obj("type" -> nodeType)))
                        )
                        
                        // Push into the channel
                        channel.push(new DataPacket(List(Map(resultName -> newObject))))
                        
                        newObject
                    }
                }).toList
                
                // See if we need to collect comments if these are just posts
                if (!pr.is_comment) commentCollector ! new PostList(resultList, false)
            })
        }
    }
}

class FacebookGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var postCollector: ActorRef = _
    var commentCollector: ActorRef = _
    var authorCollector: ActorRef = _
    var users: Array[String] = _
    var updateTime: Long = _
    var postsScheduler: Cancellable = _
    var commentsScheduler: Cancellable = _
    var postIteratorStart: Long = _
    var postIteratorEnd: Option[Long] = _
    
    override def _receive = {
        case sp: StopPacket => {
            Future.sequence(List(postCollector ? new StopPacket,
                commentCollector ? new StopPacket,
                authorCollector ? new StopPacket
            )).map {
                case _ => {
                    postsScheduler.cancel
                    commentsScheduler.cancel
                    cleanup
                }
            }
        }
        case ip: IteratePosts => {
            // Collect next round
            val now = System.currentTimeMillis / 1000L
            postCollector ! new PostCollectorPacket(users.toList, postIteratorStart, Some(postIteratorEnd.getOrElse(now)))
            postIteratorStart = postIteratorEnd.getOrElse(now)
        }
        case config: JsValue => {
            // Get credentials
            val credentials = (config \ "credentials").as[JsObject]
            val aToken = (credentials \ "access_token").as[String]
            // Get update time
            val updateTime = (config \ "update_time").asOpt[Long].getOrElse(5L)
            // Since we get all data back to start time in one go, we might just stop there
            val runOnce = (config \ "run_once").asOpt[Boolean].getOrElse(false)
            
            // Get flush and comment intervals
            val flushInterval = (config \ "flush_interval").asOpt[Int].getOrElse(60)
            val commentInterval = (config \ "comment_interval").asOpt[Int].getOrElse(3600)
            val commentFrequency = (config \ "comment_frequency").asOpt[Int].getOrElse(5)
            
            // Set up RestFB
            val fbClient = new DefaultFacebookClient(aToken, Version.VERSION_2_8)

            // Filters that we need to check
            val (_, userids, _) = Common.getFilters(config)
            users = userids.map(_ + "/feed")
                
            // Stop if there are no users
            if (users.size == 0) self ! new StopPacket
            else {
                // Check period, if given
                val interval = (config \ "interval").asOpt[JsObject]
                val now = System.currentTimeMillis / 1000L
                var (startTime: Long, endTime: Option[Long]) = interval match {
                    case Some(intvl) => {
                        ((intvl \ "start").asOpt[Long].getOrElse(now),
                            (intvl \ "end").asOpt[Long])
                    }
                    case None => (now, None)
                }
                postIteratorStart = now
                postIteratorEnd = endTime
    
                // Set up actors
                authorCollector = Akka.system.actorOf(Props(classOf[AuthorCollector], fbClient, channel, resultName))
                commentCollector = Akka.system.actorOf(Props(classOf[CommentCollector], fbClient, authorCollector, commentFrequency))
                authorCollector ! commentCollector
                postCollector = Akka.system.actorOf(Props(classOf[PostCollector], fbClient, commentCollector, authorCollector, flushInterval))
                
                // Kick it off
                postCollector ! new PostCollectorPacket(users.toList, startTime, endTime)
                // Keep going until we get to end time
                postsScheduler = context.system.scheduler.schedule(updateTime seconds, updateTime seconds, self, new IteratePosts())
                commentsScheduler = context.system.scheduler.schedule(commentInterval seconds, commentInterval seconds, commentCollector, new IterateComments())
            }
        }
    }
}