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

case class FBDataRequest(
    urls: List[String],
    start: Option[Long],
    end: Option[Long]
)

/**
 * Gets all posts from a facebook page
 */
class AsyncFacebookCollector(parentActor: ActorRef, fbClient: DefaultFacebookClient, updateTime: Long, fields: String, getComments: Boolean, runOnce: Boolean) extends Actor with ActorLogging {
    // If we need to get the comments too, make sure we set up the actor
    val commentsActor = if (getComments) Some(Akka.system.actorOf(Props(classOf[CommentsCollector], parentActor, fbClient))) else None
    val postIds = collection.mutable.ListBuffer.empty[String]
    
    def receive() = {
        case fbdr: FBDataRequest => {
            val urls = fbdr.urls
            val now = System.currentTimeMillis / 1000
            // Get start and end time
            val startTime = fbdr.start match {
                case Some(st) => st
                case None     => System.currentTimeMillis / 1000;
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
            
            val resultList = (for ((response, index) <- responses.zipWithIndex) yield {
                val objectList = new Connection[JsonObject](fbClient, response.getBody, classOf[JsonObject])
                for (objects <- objectList) yield {
                    for (obj <- objects) {
                        // Get the post
                        val post = fbClient.getJsonMapper.toJavaObject(obj.toString, classOf[Post])
                        
                        // Parse time and all that
                        val unixTime = post.getCreatedTime.getTime / 1000
                        // See if the creation time was within our interval
                        if (unixTime <= endTime && unixTime >= startTime) {
                                // Send result to parent
                                parentActor ! new ResponsePacket(Json.parse(obj.toString).asInstanceOf[JsObject]
                                        ++ Json.obj("is_comment" -> false))
                                // Get comments if we have to
                                commentsActor match {
                                    case Some(ca) if postIds.size == 10 => {
                                        // Get comments
                                        ca ! new FBDataRequest(postIds.toList, fbdr.start, fbdr.end)
                                        postIds.clear
                                    }
                                    case Some(ca) => postIds += post.getId
                                    case None => {}
                                }
                        }
                    }
                }
            }).toList
            
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
 * Collects comments of posts
 */
class CommentsCollector(parentActor: ActorRef, fbClient: DefaultFacebookClient) extends Actor with ActorLogging {
    def receive() = {
        case fbdr: FBDataRequest => {
            // Make sure we get all the comments of all the post IDs given
            val urls = fbdr.urls.map(_ + "/comments")
            val now = System.currentTimeMillis / 1000
            // Get start and end time
            val startTime = fbdr.start match {
                case Some(st) => st
                case None     => System.currentTimeMillis / 1000;
            }
            val endTime = fbdr.end match {
                case Some(en) => en
                case None     => 13592062088L // Some incredibly large number
            }
            
            // Get the URLs in batched fasion
            val requests = for (url <- urls) yield {
                // Build the start and end parameters
                val parameters = Array(Parameter.`with`("limit", 50), Parameter.`with`("fields", "id,attachment,comment_count,created_time,from,like_count,message,message_tags,object,parent,user_likes")) ++ {
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
                // Weird bug in RestFB when data field cannot be found
                try {
                    val objectList = new Connection[JsonObject](fbClient, response.getBody, classOf[JsonObject])
                    for (objects <- objectList) {
                        objects.foreach(obj => {
                            // Send result to parent
                            parentActor ! new ResponsePacket(Json.parse(obj.toString).asInstanceOf[JsObject]
                                ++ Json.obj("is_comment" -> true))
                        })
                    }
                } catch {
                    case e: com.restfb.json.JsonException => List()
                }
            }
        }
    }
}

class FacebookGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    val allFields = List(
            "id",
            "application",
            "call_to_action",
            "caption",
            "child_attachments",
            "comments_mirroring_domain",
            "coordinates",
            "created_time",
            "description",
            "event",
            "expanded_height",
            "expanded_width",
            "feed_targeting",
            "full_picture",
            "height",
            "icon",
            "instagram_eligibility",
            "is_expired",
            "is_hidden",
            "is_instagram_eligible",
            "is_popular",
            "is_published",
            "is_spherical",
            "link",
            "message",
            "message_tags",
            "name",
            "object_id",
            "parent_id",
            "permalink_url",
            "picture",
            "place",
            "privacy",
            "promotion_status",
            "properties",
            "scheduled_publish_time",
            "shares",
            "source",
            "status_type",
            "story",
            "story_tags",
            "target",
            "targeting",
            "timeline_visibility",
            "type",
            "updated_time",
            "via",
            "width",
            "likes.summary(true)",
            "from{id,about,affiliation,artists_we_like,attire,awards,band_interests,band_members,best_page,bio,birthday,booking_agent,built,business,can_checkin,can_post,category,category_list,checkins,company_overview,contact_address,country_page_likes,cover,culinary_team,current_location,description,description_html,directed_by,display_subtext,emails,features,food_styles,founded,general_info,general_manager,genre,global_brand_page_name,global_brand_root_id,has_added_app,hometown,hours,influences,is_community_page,is_permanently_closed,is_published,is_unclaimed,is_verified,leadgen_tos_accepted,link,location,members,mission,mpg,name,network,new_like_count,offer_eligible,overall_star_rating,parent_page,parking,payment_options,personal_info,personal_interests,pharma_safety_info,phone,place_type,plot_outline,press_contact,price_range,produced_by,products,promotion_ineligible_reason,public_transit,publisher_space,rating_count,record_label,release_date,restaurant_services,restaurant_specialties,schedule,screenplay_by,season,single_line_address,starring,store_number,studio,talking_about_count,unread_message_count,unread_notif_count,unseen_message_count,username,voip_info,website,were_here_count,written_by}",
            "to{id,about,affiliation,artists_we_like,attire,awards,band_interests,band_members,best_page,bio,birthday,booking_agent,built,business,can_checkin,can_post,category,category_list,checkins,company_overview,contact_address,country_page_likes,cover,culinary_team,current_location,description,description_html,directed_by,display_subtext,emails,features,food_styles,founded,general_info,general_manager,genre,global_brand_page_name,global_brand_root_id,has_added_app,hometown,hours,influences,is_community_page,is_permanently_closed,is_published,is_unclaimed,is_verified,leadgen_tos_accepted,link,location,members,mission,mpg,name,network,new_like_count,offer_eligible,overall_star_rating,parent_page,parking,payment_options,personal_info,personal_interests,pharma_safety_info,phone,place_type,plot_outline,press_contact,price_range,produced_by,products,promotion_ineligible_reason,public_transit,publisher_space,rating_count,record_label,release_date,restaurant_services,restaurant_specialties,schedule,screenplay_by,season,single_line_address,starring,store_number,studio,talking_about_count,unread_message_count,unread_notif_count,unseen_message_count,username,voip_info,website,were_here_count,written_by}"
    )
    
    var pollerActor: ActorRef = _
    
    override def _receive = {
        case sp: StopPacket => {
            pollerActor ! PoisonPill
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
            
            // Set up RestFB
            val fbClient = new DefaultFacebookClient(aToken, Version.VERSION_2_8)

            // Filters that we need to check
            val filters = Common.getFilters(config)
            val users = filters("userids")
                .asInstanceOf[Array[String]].map(_ + "/feed")

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
            pollerActor = Akka.system.actorOf(Props(classOf[AsyncFacebookCollector], self, fbClient, updateTime, fields, getComments, runOnce))
            pollerActor ! new FBDataRequest(users.toList, startTime, endTime)
        }
        case data: ResponsePacket => channel.push(DataPacket(List(Map(resultName -> data.json))))
    }
}