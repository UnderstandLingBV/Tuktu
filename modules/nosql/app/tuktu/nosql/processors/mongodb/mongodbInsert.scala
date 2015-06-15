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

/**
 * Inserts data into MongoDB
 */
class MongoDBInsertProcessor(resultName: String) extends BaseProcessor(resultName) {
    var connection: MongoConnection = _
    var collection: JSONCollection = _
    
    var fields = List[String]()
    var sync = false

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
        
        //Should we write is synchronized?
        sync = (config \ "sync").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = {
        if (sync) Enumeratee.map((data: DataPacket) => insert(data))
        else Enumeratee.mapM((data: DataPacket) => Future {insert(data)})
    }  compose Enumeratee.onEOF(() => connection.close)
    
    def insert(data: DataPacket) = {
        // Insert data into MongoDB
        data.data.foreach(datum => fields match {
            case Nil => collection.insert(anyMapToJson(datum, true))
            case _ => collection.insert(anyMapToJson(datum.filter(elem => fields.contains(elem._1)), true))
        })
        
        data
    }    
}