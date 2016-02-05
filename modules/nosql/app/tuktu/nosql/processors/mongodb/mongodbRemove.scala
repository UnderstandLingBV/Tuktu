package tuktu.nosql.processors.mongodb

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import reactivemongo.core.nodeset.Authenticate
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.nosql.util.MongoTools
import tuktu.nosql.util.MongoSettings
import tuktu.nosql.util.stringHandler
import scala.util.Failure
import scala.util.Success

/**
 * Removes data from MongoDB
 */
class MongoDBRemoveProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fcollection: Future[JSONCollection] = _
    var settings: MongoSettings = _
    var query: String = _
    var filter: String = _
    var justOne: Boolean = _
    var timeout: Int = _
    var blocking: Boolean = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]

        // Get credentials
        val user = (config \ "user").asOpt[String]
        val pwd = (config \ "password").asOpt[String].getOrElse("")
        val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        val scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

        // Set up connection
        settings = MongoSettings(hosts, database, coll)
        fcollection = user match{
            case None => MongoTools.getFutureCollection(this, settings)
            case Some( usr ) => {
                val credentials = admin match
                {
                  case true => Authenticate( "admin", usr, pwd )
                  case false => Authenticate( database, usr, pwd )
                }
                MongoTools.getFutureCollection(this, settings, credentials, scramsha1)
              }
          }

        // Get query and filter
        query = (config \ "query").as[String]

        // Only delete maximum of one item?
        justOne = (config \ "just_one").asOpt[Boolean].getOrElse(false)
        
        // Wait for deletion to complete?
        blocking = (config \ "blocking").asOpt[Boolean].getOrElse(true)

        // Maximum time out
        timeout = (config \ "timeout").asOpt[Int].getOrElse(Cache.getAs[Int]("timeout").getOrElse(30))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => if (!blocking) {
        Future {
            doRemove(data)
            data
        }
    } else {
        // Wait for the removal to be finished
        doRemove(data).map {
            case _ => data
        }
    }) compose Enumeratee.onEOF { () => MongoTools.deleteCollection(this, settings) }
    
    
    // Does the actual removal
    def doRemove(data: DataPacket) = {
        // Remove data from MongoDB
        val queries = (for (datum <- data.data) yield {
            Json.parse(stringHandler.evaluateString(query, datum, "\"", ""))
        }).distinct
        fcollection.flatMap{ collection => collection.remove[JsObject](Json.obj("$or" -> queries), firstMatchOnly = justOne) }
    } 
}