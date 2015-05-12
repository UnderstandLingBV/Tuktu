package tuktu.nosql.processors.mongodb

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api._
import tuktu.api.utils.anyMapToJson
import scala.concurrent.Future
import play.api.libs.json.JsObject
import reactivemongo.api._
import play.api.libs.iteratee.Iteratee
import play.modules.reactivemongo.json.collection.JSONCollection
import play.api.libs.json.Json

/**
 * Inserts data into MongoDB
 */
class MongoDBInsertProcessor(resultName: String) extends BaseProcessor(resultName) {
    var connection: MongoConnection = _
    var collection: JSONCollection = _
    
    var fields = List[String]()

    override def initialize(config: JsObject) = {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]

        // Set up connection
        val driver = new MongoDriver
        connection = driver.connection(hosts)
        // Connect to DB
        val db = connection(database)
        // Select the collection
        collection = db(coll)
        
        // What fields to write?
        fields = (config \ "fields").as[List[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        // Insert data into MongoDB
        data.data.foreach(datum => {
            collection.insert(anyMapToJson(datum.filter(elem => fields.contains(elem._1))))
        })
        
        data
    })
}