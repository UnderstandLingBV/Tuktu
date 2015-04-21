package tuktu.nosql.processors

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api._
import scala.concurrent.Future
import play.api.libs.json.JsObject
import reactivemongo.api._
import play.api.libs.iteratee.Iteratee
import play.modules.reactivemongo.json.collection.JSONCollection

/**
 * Queries MongoDB for data
 */
// TODO: Support dynamic querying, is now static
class MongoDBFindProcessor(resultName: String) extends BaseProcessor(resultName) {
    var connection: MongoConnection = _
    var collection: JSONCollection = _

    var query: JsObject = _
    var filter: JsObject = _
    
    var overwrite: Boolean = false
    var append: Boolean = false

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

        // Get query and filter
        query = (config \ "query").as[JsObject]
        filter = (config \ "filter").as[JsObject]
        
        // Overwrite or append?
        overwrite = (config \ "overwrite").asOpt[Boolean].getOrElse(false)
        append = (config \ "append").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        // Get data based on query and filter
        val resultData = collection.find(query, filter).cursor[JsObject].collect[List]()
            
        resultData.map { resultList => {
            new DataPacket(for (resultRow <- resultList) yield {
                Map(resultName -> resultRow)
            })
        }}
    })
}