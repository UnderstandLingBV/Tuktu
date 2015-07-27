package tuktu.nosql.processors.mongodb

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api._
import scala.concurrent.Future
import play.api.libs.json.JsObject
import reactivemongo.api._
import play.api.libs.iteratee.Iteratee
import play.modules.reactivemongo.json.collection.JSONCollection
import scala.concurrent.Await
import play.api.cache.Cache
import scala.concurrent.duration.DurationInt
import play.api.libs.json.Json
import tuktu.nosql.util.sql
import tuktu.nosql.util.stringHandler
import tuktu.nosql.util.MongoCollectionPool
import tuktu.nosql.util.MongoSettings

/**
 * Queries MongoDB for data
 */
// TODO: Support dynamic querying, is now static
class MongoDBFindProcessor(resultName: String) extends BaseProcessor(resultName) {
    var collection: JSONCollection = _
    var query: String = _
    var filter: String = _
    var sort: String = _
    var limit: Option[Int] = _

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
        filter = (config \ "filter").asOpt[String].getOrElse("{}")
        sort = (config \ "sort").asOpt[String].getOrElse("{}")
        limit = (config \ "limit").asOpt[Int]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Get data from Mongo and sequence
        val resultListFutures = Future.sequence(for(datum <- data.data) yield {
            // Evaluate the query en filter strings and convert to JSON
            val queryJson = Json.parse(stringHandler.evaluateString(query, datum,"\"",""))
            val filterJson = Json.parse(utils.evaluateTuktuString(filter, datum))
            val sortJson = Json.parse(utils.evaluateTuktuString(sort, datum)).asInstanceOf[JsObject]
            // Get data based on query and filter
            val resultData = limit match {
                case Some(s) => collection.find(queryJson,filterJson).sort(sortJson).options(QueryOpts().batchSize(s)).cursor[JsObject].collect[List](s)
                case None => collection.find(queryJson,filterJson).sort(sortJson).cursor[JsObject].collect[List]()
            }   
            
            resultData.map { resultList => {
                for (resultRow <- resultList) yield {
                    tuktu.api.utils.JsObjectToMap(resultRow)
                }
            }}    
        })
        
        // Iterate over result futures
        val dataFuture = resultListFutures.map(resultList => {
            // Combine result with data
            for ((result, datum) <- resultList.zip(data.data)) yield {
                datum + (resultName -> result)
            }
        })
        
        // Gather results
        new DataPacket(Await.result(dataFuture, Cache.getAs[Int]("timeout").getOrElse(5) seconds))
    }) 
}