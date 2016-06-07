package tuktu.nosql.processors.mongodb

import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.core.nodeset.Authenticate
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.nosql.util._
import play.api.cache.Cache
import play.api.Play.current
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import play.api.libs.json._

/**
 * Updates data into MongoDB
 */
class MongoDBUpdateProcessor(resultName: String) extends BaseProcessor(resultName)
{
    // the collection to write to and its settings
    var settings: MongoDBSettings = _
    var fcollection: Future[JSONCollection] = _
    // If set to true, creates a new document when no document matches the query criteria. 
    var upsert = false
    // The selection criteria for the update. 
    var query: String = _
    // The modifications to apply. 
    var update: String = _
    //  If set to true, updates multiple documents that meet the query criteria. If set to false, updates one document. 
    var multi = false
    var blocking: Boolean = _
    var conn: Int = _

    override def initialize(config: JsObject) 
    {
        // Set up MongoDB client
        val hs = (config \ "hosts").as[List[String]]
        val hosts = SortedSet(hs: _*)
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]
        query = (config \ "query").as[String]
        update = (config \ "update").as[String]
        upsert = (config \ "upsert").asOpt[Boolean].getOrElse(false)
        multi = (config \ "multi").asOpt[Boolean].getOrElse(false)
        blocking = (config \ "blocking").asOpt[Boolean].getOrElse(true)
        conn = (config \ "connections").asOpt[Int].getOrElse(10)
        // Get credentials
        val user = (config \ "user").asOpt[String]
        val pwd = (config \ "password").asOpt[String].getOrElse("")
        val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        val scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

        // Set up connection
        settings = MongoDBSettings(hosts, database, coll)
        fcollection = user match{
            case None => mongoTools.getFutureCollection(settings, conn)
            case Some( usr ) => {
                val credentials = admin match
                {
                  case true => Authenticate( "admin", usr, pwd )
                  case false => Authenticate( database, usr, pwd )
                }
                mongoTools.getFutureCollection(settings,credentials, scramsha1, conn)
              }
          }
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        if (!blocking) 
        {
            Future {
                doUpdate(data)
                data
            }
        } else {
            // Wait for all the updates to be finished
            doUpdate(data).map {
                case _ => data
            }
        }
    })
    
    
    // Does the actual updating
    def doUpdate(data: DataPacket) = {
        // Update data into MongoDB
        Future.sequence(data.data.map(datum => {
            val selector = Json.parse(stringHandler.evaluateString(query, datum, "\"", "")).as[JsObject]
            println( "selector: " + selector  )
            val updater = Json.parse(stringHandler.evaluateString(update, datum, "\"", "")).as[JsObject]
            println( "updater : " + updater  )
            fcollection.flatMap{ collection => collection.update(selector, updater, upsert = upsert, multi = multi) }
        }))
    }    
}