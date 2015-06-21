package tuktu.nosql.processors.mongodb

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.MongoConnection
import reactivemongo.api.MongoDriver
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils.anyMapToJson
import tuktu.nosql.util.MongoCollectionPool
import tuktu.nosql.util.MongoSettings
import play.api.cache.Cache
import play.api.Play.current
import scala.concurrent.duration._
import scala.concurrent.Await

/**
 * Inserts data into MongoDB
 */
class MongoDBInsertProcessor(resultName: String) extends BaseProcessor(resultName) { 
    var fields = List[String]()
    var collection: JSONCollection = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]
        
        // create connectionPool
        val settings = MongoSettings(hosts, database, coll)
        collection = MongoCollectionPool.getCollection(settings)

        // What fields to write?
        fields = (config \ "fields").as[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {        
        // Insert data into MongoDB
        val futures = data.data.map(datum => fields match {
            case Nil => collection.insert(anyMapToJson(datum, true))
            case _ => collection.insert(anyMapToJson(datum.filter(elem => fields.contains(elem._1)), true))
        })
        // Wait for all the results to be retrieved
        futures.foreach { f => if(!f.isCompleted) Await.ready(f, Cache.getAs[Int]("timeout").getOrElse(5) seconds) }
        
        data
    }) 
}