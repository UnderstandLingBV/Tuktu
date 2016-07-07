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
import reactivemongo.api.MongoConnection
import akka.util.Timeout
import tuktu.api.utils

/**
 * Updates data into MongoDB
 */
class MongoDBUpdateProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var conn: MongoConnection = _
    var nodes: List[String] = _
    
    var db: String = _
    var collection: String = _
    
    var waitForCompletion: Boolean = _
    
    // If set to true, creates a new document when no document matches the query criteria. 
    var upsert = false
    // The selection criteria for the update. 
    var query: String = _
    // The modifications to apply. 
    var update: String = _
    //  If set to true, updates multiple documents that meet the query criteria. If set to false, updates one document. 
    var multi = false

    override def initialize(config: JsObject) {
        // Get hosts
        nodes = (config \ "hosts").as[List[String]]
        // Get connection properties
        val opts = (config \ "mongo_options").asOpt[JsObject]
        val mongoOptions = MongoPool.parseMongoOptions(opts)
        // Get credentials
        val authentication = (config \ "auth").asOpt[JsObject]
        val auth = authentication match {
            case None => None
            case Some(a) => Some(Authenticate(
                    (a \ "db").as[String],
                    (a \ "user").as[String],
                    (a \ "password").as[String]
            ))
        }
        
        // DB and collection
        db = (config \ "db").as[String]
        collection = (config \ "collection").as[String]

        // Query stuff
        query = (config \ "query").as[String]
        update = (config \ "update").as[String]
        upsert = (config \ "upsert").asOpt[Boolean].getOrElse(false)
        multi = (config \ "multi").asOpt[Boolean].getOrElse(false)
        
        // Wait for updates to complete?
        waitForCompletion = (config \ "wait_for_completion").asOpt[Boolean].getOrElse(false)
        
        // Get the connection
        val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
        conn = Await.result(fConnection, timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val jsons = (for (datum <- data.data) yield {
            (
                utils.evaluateTuktuString(db, datum),
                utils.evaluateTuktuString(collection, datum),
                (
                        Json.parse(stringHandler.evaluateString(query, datum, "\"", "")).as[JsObject],
                        Json.parse(stringHandler.evaluateString(update, datum, "\"", "")).as[JsObject]
                )
            )
        }).toList.groupBy(_._1).map(elem => elem._1 -> elem._2.groupBy(_._2))
        
        // Execute per DB/Collection pair
        val resultFut = Future.sequence(for {
            (dbEval, collectionMap) <- jsons
            (collEval, queries) <- collectionMap
            (selector, updater) <- queries.map(_._3)
        } yield {
            val fCollection = MongoPool.getCollection(conn, dbEval, collEval)
            fCollection.flatMap(coll => coll.update(selector, updater, upsert = upsert, multi = multi))
        })

        // Continue directly or wait?
        if (waitForCompletion) resultFut.map { _ => data }
        else Future { data }
    }) compose Enumeratee.onEOF(() => MongoPool.releaseConnection(nodes, conn))
}