package tuktu.nosql.processors.mongodb

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.core.nodeset.Authenticate
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.nosql.util.MongoCollectionPool
import tuktu.nosql.util.MongoSettings
import play.api.cache.Cache
import play.api.Play.current
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import play.api.libs.json._
import tuktu.api.utils.MapToJsObject

/**
 * Updates datum into MongoDB (assuming it was initially found in MongoDB)
 */
class MongoDBUpdatumProcessor(resultName: String) extends BaseProcessor(resultName) {
    // the collection to write to
    var collection: JSONCollection = _
    // If set to true, creates a new document when no document matches the _id key. 
    var upsert: Boolean = _
    var blocking: Boolean = _
    var field: Option[String] = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]
        upsert = (config \ "upsert").asOpt[Boolean].getOrElse(false)
        blocking = (config \ "blocking").asOpt[Boolean].getOrElse(true)
        // Get credentials
        val user = (config \ "user").asOpt[String]
        val pwd = (config \ "password").asOpt[String].getOrElse("")
        val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        val scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)
        // Use field instead of datum?
        field = (config \ "field").asOpt[String]

        // Set up connection
        val settings = MongoSettings(hosts, database, coll)
        collection = user match{
            case None => MongoCollectionPool.getCollection(settings)
            case Some( usr ) => {
                val credentials = admin match
                {
                  case true => Authenticate( "admin", usr, pwd )
                  case false => Authenticate( database, usr, pwd )
                }
                MongoCollectionPool.getCollectionWithCredentials(settings,credentials, scramsha1)
              }
          }
    }
    
    // Does the actual updating
    def doUpdate(data: DataPacket) = {
        // Update data into MongoDB
        Future.sequence(data.data.map(datum => {
            val updater = field match
            {
              case None => MapToJsObject( datum )
              case Some( f ) => datum(f) match{
                 case j: JsObject => j
                 case m: Map[String, Any] => MapToJsObject( m ) 
              }
            }
            val selector = Json.obj( "_id" ->   (updater \ "_id") )
            collection.update(selector, updater, upsert = upsert)
        }))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => if (blocking) {
        Future {
            doUpdate(data)
            data
        }
    } else {
        // Wait for all the updates to be finished
        doUpdate(data).map {
            case _ => data
        }
    })
}