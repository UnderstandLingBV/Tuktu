package tuktu.nosql.processors.mongodb

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsObject
import play.modules.reactivemongo.json.collection.JSONCollection
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils.anyMapToJson
import tuktu.nosql.util.MongoCollectionPool
import tuktu.nosql.util.MongoSettings

/**
 * Inserts data into MongoDB
 */
class MongoDBInsertProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields = List[String]()
    var collection: JSONCollection = _
    var timeout: Int = _

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

        timeout = (config \ "timeout").asOpt[Int].getOrElse(Cache.getAs[Int]("timeout").getOrElse(30))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Convert to JSON
        val bulkData = data.data.map(datum => fields match {
            case Nil => anyMapToJson(datum, true)
            case _   => anyMapToJson(datum.filter(elem => fields.contains(elem._1)), true)
        })

        // Bulk insert and await
        val future = collection.bulkInsert(Enumerator.enumerate(bulkData))

        // Wait for all the results to be retrieved
        Await.ready(future, timeout seconds)

        data
    })
}