package tuktu.nosql.processors.mongodb

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.modules.reactivemongo.json.collection.JSONCollection
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.nosql.util.MongoCollectionPool
import tuktu.nosql.util.MongoSettings
import tuktu.nosql.util.stringHandler
import scala.util.Failure
import scala.util.Success

/**
 * Removes data from MongoDB
 */
class MongoDBRemoveProcessor(resultName: String) extends BaseProcessor(resultName) {
    var collection: JSONCollection = _
    var query: String = _
    var filter: String = _
    var justOne = false
    var timeout: Int = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]

        // Set up connection
        val settings = MongoSettings(hosts, database, coll)
        collection = MongoCollectionPool.getCollection(settings)

        // Get query and filter
        query = (config \ "query").as[String]
        
        // Only delete maximum of one item?
        justOne = (config \ "just_one").asOpt[Boolean].getOrElse(false)
        
        // Maximum time out
        timeout = (config \ "timeout").asOpt[Int].getOrElse(Cache.getAs[Int]("timeout").getOrElse(5))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // create one big remove query
        val queries = (for(datum <- data.data) yield {
            Json.parse(stringHandler.evaluateString(query, datum,"\"","")).toString()
        }).mkString(",")

        // execute and wait for completion
        val result = collection.remove(Json.parse("{ \"$or\": [ "+queries+" ] }"))
        Await.ready(result, timeout seconds)
        
        // return original data
        data
    }) 
}