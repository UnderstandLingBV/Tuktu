package tuktu.nosql.processors.mongodb

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.{ BaseProcessor, DataPacket }
import tuktu.api.utils.{ MapToJsObject, evaluateTuktuString }
import tuktu.nosql.util.{ MongoSettings, MongoCollectionPool }

/**
 * Inserts data into MongoDB
 */
class MongoDBInsertProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields = List[String]()
    var hosts: List[String] = _
    var database: String = _
    var coll: String = _
    var timeout: Int = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        hosts = (config \ "hosts").as[List[String]]
        database = (config \ "database").as[String]
        coll = (config \ "collection").as[String]

        // What fields to write?
        fields = (config \ "fields").as[List[String]]

        timeout = (config \ "timeout").asOpt[Int].getOrElse(Cache.getAs[Int]("timeout").getOrElse(30))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        val result = scala.collection.mutable.Map[(List[String], String, String), scala.collection.mutable.ListBuffer[JsObject]]()

        // Convert to JSON
        for (datum <- data.data) {
            result.getOrElseUpdate((hosts.map(evaluateTuktuString(_, datum)), evaluateTuktuString(database, datum), evaluateTuktuString(coll, datum)), scala.collection.mutable.ListBuffer[JsObject]()) += {
                fields match {
                    case Nil => MapToJsObject(datum, true)
                    case _   => MapToJsObject(datum.filter(elem => fields.contains(elem._1)), true)
                }
            }
        }

        // Bulk insert and await
        val futures = for (f <- result) yield {
            val collection = MongoCollectionPool.getCollection(MongoSettings(f._1._1, f._1._2, f._1._3))
            collection.bulkInsert(f._2.toStream, false)
        }
        // Wait for all the results to be retrieved
        futures.foreach { x => if (!x.isCompleted) Await.ready(x, timeout seconds) }

        data
    })
}