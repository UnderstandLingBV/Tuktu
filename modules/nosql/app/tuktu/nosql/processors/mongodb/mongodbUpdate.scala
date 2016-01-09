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
import tuktu.nosql.util.stringHandler

/**
 * Updates data into MongoDB
 */
class MongoDBUpdateProcessor(resultName: String) extends BaseProcessor(resultName) {
    // the collection to write to
    var collection: JSONCollection = _
    // If set to true, creates a new document when no document matches the query criteria. 
    var upsert = false
    // The selection criteria for the update. 
    var query: String = _
    // The modifications to apply. 
    var update: String = _
    //  If set to true, updates multiple documents that meet the query criteria. If set to false, updates one document. 
    var multi = false
    var blocking: Boolean = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]
        query = (config \ "query").as[String]
        update = (config \ "update").as[String]
        upsert = (config \ "upsert").asOpt[Boolean].getOrElse(false)
        multi = (config \ "multi").asOpt[Boolean].getOrElse(false)
        blocking = (config \ "blocking").asOpt[Boolean].getOrElse(true)
        // Get credentials
        val user = (config \ "user").asOpt[String]
        val pwd = (config \ "password").asOpt[String].getOrElse("")
        val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        val scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

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
    
    
    // Does the actual updating
    def doUpdate(data: DataPacket) = {
        // Update data into MongoDB
        Future.sequence(data.data.map(datum => {
            val selector = Json.parse(stringHandler.evaluateString(query, datum, "\"", "")).as[JsObject]
            val updater = Json.parse(stringHandler.evaluateString(update, datum, "\"", "")).as[JsObject]
            collection.update(selector, updater, upsert = upsert, multi = multi)
        }))
    }    
}