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
    var settings: MongoSettings = _
    var credentials: Option[Authenticate] = _
    var scramsha1: Boolean = _
    // If set to true, creates a new document when no document matches the _id key. 
    var upsert: Boolean = _
    var blocking: Boolean = _
    var field: Option[String] = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]
        settings = MongoSettings(hosts, database, coll)
        
        upsert = (config \ "upsert").asOpt[Boolean].getOrElse(false)
        blocking = (config \ "blocking").asOpt[Boolean].getOrElse(true)
        // Get credentials
        val user = (config \ "user").asOpt[String]
        val pwd = (config \ "password").asOpt[String].getOrElse("")
        val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        credentials = user match
        {
            case None => None
            case Some( usr ) => {
                admin match
                {
                  case true => Option(Authenticate( "admin", usr, pwd ))
                  case false => Option(Authenticate( database, usr, pwd ))
                }
            }
        }
        scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)
        // Use field instead of datum?
        field = (config \ "field").asOpt[String]
    }
    
    // Does the actual updating
    def doUpdate(data: DataPacket) = {
        // Update data into MongoDB
        Future.sequence(data.data.map(datum => {
            val setts = MongoSettings( settings.hosts.map{ host => tuktu.api.utils.evaluateTuktuString(host, datum)}, tuktu.api.utils.evaluateTuktuString(settings.database, datum), tuktu.api.utils.evaluateTuktuString(settings.collection, datum))
            val collection = credentials match{
                case None => MongoCollectionPool.getCollection(setts)
                case Some( creds ) => MongoCollectionPool.getCollectionWithCredentials(setts, creds, scramsha1)
            }
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

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => if (!blocking) {
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