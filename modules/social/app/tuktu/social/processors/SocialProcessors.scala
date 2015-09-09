package tuktu.social.processors

import scala.concurrent.Future

import org.scribe.builder.ServiceBuilder
import org.scribe.builder.api.FacebookApi
import org.scribe.model.OAuthRequest
import org.scribe.model.Token
import org.scribe.model.Verb
import org.scribe.oauth.OAuthService

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * When a tweet is being searched for on the stream using a combination of filters, this processors will append to the data
 * those filters that actually caused a hit for this tweet
 */
class TwitterTaggerProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Get name of the field in which the Twitter object is
    var objField = ""
    // Get the actual tags
    var tags: JsObject = Json.obj()
    var keywords: Option[List[String]] = None
    var users: Option[List[String]] = None
    var geos: Option[List[String]] = None
    var excludeOnNone = false
    
    override def initialize(config: JsObject) {
        // Get name of the field in which the Twitter object is
        objField = (config \ "object_field").as[String]
        // Get the actual tags
        tags = (config \ "tags").as[JsObject]
        keywords = (tags \ "keywords").asOpt[List[String]]
        users = (tags \ "users").asOpt[List[String]]
        geos = (tags \ "geos").asOpt[List[String]]
        excludeOnNone = (config \ "exclude_on_none").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        Future {new DataPacket(for {
            datum <- data.data
            
            tweet = datum(objField).asInstanceOf[JsObject]
            tags = Map(
	            // Keyword tagging
	            "keywords" -> (keywords match {
	                case Some(kw) => {
	                    // Get the tweet body and see which keywords occur
	                    val tw = (tweet \ "text").as[String]
	                    kw.filter(k => tw.toLowerCase.contains(k.toLowerCase))
	                }
	                case None => List()
	            }),
	            "users" -> (users match {
	                case Some(usrs) => {
	                    // User could be in a number of places actually, so we need to search a bit more extensive than just tweet author ID
	                    val tw = (tweet \ "text").as[String]
	                    val author = (tweet \ "user" \ "id").as[Long].toString
	                    val inReplyId = try {
	                        (tweet \ "inReplyToUserId").as[Long].toString
	                    } catch {
	                        case _: Throwable => "-1"
	                    }
	                    val retweetId = try {
	                        (tweet \ "retweetedStatus" \ "user" \ "id").as[Long].toString
	                    } catch {
	                        case _: Throwable => "-1"
	                    }
	                    val mentions = try {
	                        (tweet \ "entities" \ "user_mentions").as[List[JsObject]].map(mention => (mention \ "id").as[Long].toString)
	                    } catch {
	                        case _: Throwable => List()
	                    }
	                    
	                    // Now check for all users
	                    val res = usrs.filter(usr => {
	                        author == usr || inReplyId == usr || retweetId == usr || mentions.contains(usr)
	                    })
	                    res
	                }
	                case None => List()
	            }),
	            "geos" -> (geos match {
	                case Some(gs) => {
	                    // TODO: Implement this
	                    List()
	                }
	                case None => List()
	            })
            )
            
            // See if we need to exclude
            none = tags("keywords").isEmpty && tags("users").isEmpty && tags("geos").isEmpty
            if (!excludeOnNone || !none)
        } yield {
            // Append the tags
            datum + (resultName -> tags)
        })}
    }) compose Enumeratee.filter((data: DataPacket) => {
        data.data.size > 0
    })
}

/**
 * Does the tagging for a Facebook-originated object
 */
class FacebookTaggerProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Get name of the field in which the Twitter object is
    var objField = ""
    // Get the actual tags
    var excludeOnNone = false
    var tags: JsObject = Json.obj()
    var keywords: Option[List[String]] = None
    var users: Option[List[String]] = None
    
    override def initialize(config: JsObject) {
        // Get name of the field in which the Twitter object is
        objField = (config \ "object_field").as[String]
        // Get the actual tags
        tags = (config \ "tags").as[JsObject]
        keywords = (tags \ "keywords").asOpt[List[String]]
        users = (tags \ "users").asOpt[List[String]]
        excludeOnNone = (config \ "exclude_on_none").as[Boolean]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        Future {new DataPacket(for {
            datum <- data.data
            
            item = datum(objField).asInstanceOf[JsObject]
            tags = Map(
	            // Keyword tagging
	            "keywords" -> (keywords match {
	                case Some(kw) => {
	                    // Get the tweet body and see which keywords occur
	                    val itm = (item \ "message").asOpt[String].getOrElse("")
	                    kw.filter(k => itm.toLowerCase.contains(k.toLowerCase))
	                }
	                case None => List()
	            }),
	            "users" -> (users match {
	                case Some(usrs) => {
	                    // User could be either in the from-field or the to-field
	                    val fromName = {
	                        (item \ "from").asOpt[JsObject] match {
	                            case Some(fr) => (fr \ "name").asOpt[String].getOrElse("").toLowerCase
	                            case None => ""
	                        }
	                    }
	                    val toNames = {
	                        (item \ "to").asOpt[JsObject] match {
	                            case Some(to) => {
	                                (to \ "data").as[List[JsObject]].map(t => (t \ "name").as[String])
	                            }
	                            case None => List()
	                        }
	                    }
	                    
	                    // Now check for all users
	                    usrs.filter(usr => {
	                        fromName.toLowerCase.contains(usr) || toNames.foldLeft(false)(_ || _.toLowerCase.contains(usr))
	                    })
	                }
	                case None => List()
	            })
	        )
	        
	        // See if we need to exclude
            none = tags("keywords").isEmpty && tags("users").isEmpty
            if (!excludeOnNone || !none)
        } yield {
	        datum + (resultName -> tags)
        })}
    }) compose Enumeratee.filter((data: DataPacket) => {
        data.data.size > 0
    })
}

/**
 * Makes one single REST request
 */
class FacebookRESTProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fbClient: OAuthService = null
    var accessToken: Token = null
    
    var url = ""
    var httpMethod = Verb.GET
    
    override def initialize(config: JsObject) {
        // Set up FB client
        val consumerKey = (config \ "consumer_key").as[String]
        val consumerSecret = (config \ "consumer_secret").as[String]
        val token = (config \ "access_token").as[String] 
        fbClient = new ServiceBuilder()
            .provider(classOf[FacebookApi])
            .apiKey(consumerKey)
            .apiSecret(consumerSecret)
            .callback("http://localhost/")
            .build()
        accessToken = new Token(token, "")
        
        // Get the URL 
        url = (config \ "url").as[String]
        httpMethod = {
            (config \ "http_method").asOpt[String].getOrElse("get") match {
                case "post" => Verb.POST
                case _ => Verb.GET
            }
        }
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        
        // URLs cannot contains spaces, as such, anything surrounded by spaces can be replaced by a value present in our data
        val components = url.split(" ")
        
        Future {new DataPacket(for (datum <- data.data) yield {
	        // Replace all fields by their value
	        val replacedUrl = (for (component <- components) yield {
	            // Only do something if this is surrounded by [ and ]
	            if (component(0) == '[' && component(component.size - 1) == ']') {
	                // Replace
	                val fieldName = component.drop(1).take(component.size - 2)
	                datum(fieldName).asInstanceOf[String]
	            } else component
	        }).mkString("")
	        
	        // Make the actual request
	        val request = new OAuthRequest(httpMethod, replacedUrl)
            fbClient.signRequest(accessToken, request)
            val response = request.send
            // Get result
            val jsonResult = Json.parse(response.getBody)
	        
	        datum + (resultName -> jsonResult)
        })}
    })
}