package tuktu.social.processors

import scala.concurrent.Future

import com.github.scribejava.apis.FacebookApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth20Service

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

import scala.util.Try

/**
 * When a tweet is being searched for on the stream using a combination of filters, this processors will append to the data
 * those filters that actually caused a hit for this tweet
 */
class TwitterTaggerProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Get name of the field in which the Twitter object is
    var objField: String = _
    // Get the actual tags
    var keywords: Option[List[String]] = _
    var users: Option[List[String]] = _
    var geos: Option[List[String]] = _
    var userTagField: Option[String] = _
    var excludeOnNone: Boolean = _
    var combine: Boolean = _

    override def initialize(config: JsObject) {
        // Get name of the field in which the Twitter object is
        objField = (config \ "object_field").as[String]
        // Get the actual tags
        val tags = (config \ "tags").as[JsObject]
        keywords = (tags \ "keywords").asOpt[List[String]]
        users = (tags \ "users").asOpt[List[String]]
        geos = (tags \ "geos").asOpt[List[String]]

        userTagField = (config \ "user_tag_field").asOpt[String]

        excludeOnNone = (config \ "exclude_on_none").asOpt[Boolean].getOrElse(false)
        combine = (config \ "combined").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        DataPacket(for {
            datum <- data.data

            tweet = datum(objField).asInstanceOf[JsObject]

            k = keywords.map { list =>
                val tw: String = (tweet \ "text").as[String].toLowerCase
                list.filter { tw.contains(_) }.distinct
            }.getOrElse(Nil)

            u = users match {
                case None | Some(Nil) if (keywords == None || keywords == Some(Nil)) =>
                    // No keywords and no users were specified, we always return the author of the Tweet
                    List((tweet \ "user" \ "name").as[String])
                case None | Some(Nil) => Nil
                case Some(list) =>
                    def findMatches(objects: List[JsValue]): List[String] = Try {
                        list.map { user =>
                            // For each user try to find an object such that it matches the given user in either of the following fields
                            objects.find { obj =>
                                List("id_str", "screen_name", "name")
                                    .exists { k => (obj \ k).asOpt[String] == Some(user) }
                            } flatMap { obj =>
                                // If a match was found and userTagField is defined, use that field, otherwise just use the user
                                userTagField match {
                                    case None       => Some(user)
                                    case Some("id") => (obj \ "id_str").asOpt[String]
                                    case Some(key)  => (obj \ key).asOpt[String]
                                }
                            }
                        }.flatten.distinct
                    }.getOrElse(Nil)

                    // User could be in a number of places actually, so we need to search a bit more extensive than just tweet author ID
                    val reply = Try {
                        List(Json.obj(
                            "id_str" -> tweet \ "in_reply_to_user_id_str",
                            "screen_name" -> tweet \ "in_reply_to_screen_name",
                            // Reply does not seem to provide name, so use screen name instead
                            "name" -> tweet \ "in_reply_to_screen_name"))
                    }.getOrElse(Nil)

                    // Now check for all users
                    findMatches((tweet \ "user") :: (tweet \ "entities" \ "user_mentions").as[List[JsObject]] ++ reply)
            }

            // See if we need to exclude
            if (!excludeOnNone || !k.isEmpty || !u.isEmpty)
        } yield {
            // Append the tags
            datum + (resultName -> {
                if (combine) (k ++ u) distinct else Map("keywords" -> k, "users" -> u)
            })
        })
    })
}

/**
 * Does the tagging for a Facebook-originated object
 */
class FacebookTaggerProcessor(resultName: String) extends BaseProcessor(resultName) {
    // Get name of the field in which the Twitter object is
    var objField: String = _
    // Get the actual tags
    var users: Option[List[String]] = _
    var userTagField: Option[String] = _
    var excludeOnNone: Boolean = _
    var combine: Boolean = _

    override def initialize(config: JsObject) {
        // Get name of the field in which the Twitter object is
        objField = (config \ "object_field").as[String]

        // Get the actual tags
        val tags = (config \ "tags").as[JsObject]
        users = (tags \ "users").asOpt[List[String]]

        userTagField = (config \ "user_tag_field").asOpt[String]

        excludeOnNone = (config \ "exclude_on_none").as[Boolean]
        combine = (config \ "combined").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        DataPacket(for {
            datum <- data.data

            item = datum(objField).asInstanceOf[JsObject]

            u = users match {
                case None | Some(Nil) => (item \ "from").asOpt[JsObject] match {
                    case Some(f) => List((f \ "name").as[String])
                    case None    => Nil
                }
                case Some(usrs) =>
                    def getTag(obj: JsObject): Option[String] = {
                        val values = List("id", "username", "name").map { key => (obj \ key).asOpt[String] }.flatten
                        // If the user can be found, identify him by tagField if it is defined;
                        // or whatever he is defined by in the parameters
                        usrs.find(u => values.contains(u)).flatMap { s =>
                            userTagField match {
                                case Some(field) => (obj \ field).asOpt[String]
                                case None        => Some(s)
                            }
                        }
                    }

                    // User could be either in the from-field or the to-field
                    val from = (item \ "from").asOpt[JsObject].flatMap(getTag)
                    val to = (item \ "to").asOpt[JsObject].flatMap { t =>
                        (t \ "data").asOpt[List[JsObject]]
                    }.getOrElse(Nil).map(getTag)

                    // Remove None and flatten Some
                    (from :: to).flatten.distinct
            }

            // See if we need to exclude
            if (!excludeOnNone || !u.isEmpty)
        } yield {
            datum + (resultName -> {
                if (combine) u else Map("users" -> u)
            })
        })
    })
}

/**
 * Makes one single REST request
 */
class FacebookRESTProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fbClient: OAuth20Service = null
    var accessToken: OAuth2AccessToken = null

    var url = ""
    var httpMethod = Verb.GET

    override def initialize(config: JsObject) {
        // Set up FB client
        val consumerKey = (config \ "consumer_key").as[String]
        val consumerSecret = (config \ "consumer_secret").as[String]
        val token = (config \ "access_token").as[String]
        fbClient = new ServiceBuilder()
            .apiKey(consumerKey)
            .apiSecret(consumerSecret)
            .callback("http://localhost/")
            .build(FacebookApi.instance())
        accessToken = new OAuth2AccessToken(token, "")

        // Get the URL 
        url = (config \ "url").as[String]
        httpMethod = {
            (config \ "http_method").asOpt[String].getOrElse("get") match {
                case "post" => Verb.POST
                case _      => Verb.GET
            }
        }
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {

        // URLs cannot contains spaces, as such, anything surrounded by spaces can be replaced by a value present in our data
        val components = url.split(" ")

        for (datum <- data) yield {
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
            val request = new OAuthRequest(httpMethod, replacedUrl, fbClient)
            fbClient.signRequest(accessToken, request)
            val response = request.send
            // Get result
            val jsonResult = Json.parse(response.getBody)

            datum + (resultName -> jsonResult)
        }
    })
}