package tuktu.nosql.processors.sql

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.{ BaseProcessor, DataPacket }
import tuktu.api.utils.evaluateTuktuString
import tuktu.nosql.util.sql._
import java.sql.Connection
import tuktu.api.utils
import play.api.cache.Cache
import play.api.Play.current
import scala.concurrent.duration.DurationInt

class CacheTableProcessor(resultName: String) extends BaseProcessor(resultName) {
    var dataQuery: String = _
    var cacheName: String = _
    
    override def initialize(config: JsObject) {
        cacheName = (config \ "cache_name").as[String]
        // Query
        dataQuery = (config \ "data_query").as[String]
        
        // Check if cached
        Cache.getAs[Map[String, List[Map[String, Any]]]]("cache.table." + cacheName) match {
            case Some(table) => {
                // Already loaded
                dataQuery = (config \ "data_query").as[String]
            }
            case None => {
                // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
                val url = (config \ "url").as[String]
                val user = (config \ "user").as[String]
                val password = (config \ "password").as[String]
                val driver = (config \ "driver").as[String]
                
                // Set up the connection
                val connDef = new ConnectionDefinition(url, user, password, driver)
                var conn = getConnection(connDef)
                
                // Get the query to run
                val sqlQuery = (config \ "sql_query").as[String]
                
                // Get all the data
                val results = queryResult(sqlQuery, connDef)(conn)
                conn = results._2
                
                // Set key for lookup
                Cache.set("cache.table." + cacheName, results._1.groupBy(_((config \ "key_by").as[String]).toString)) 
                
                // Release
                releaseConnection(connDef, conn)
            }
        }
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        new DataPacket(data.data.map {datum =>
            val query = utils.evaluateTuktuString(dataQuery, datum)
            
            val records = Cache.getAs[Map[String, List[Map[String, Any]]]]("cache.table." + cacheName).get.collect {
                case row if (row._1.endsWith(".*") && row._1.startsWith(query)) || row._1 == query => row._2
            }.flatten.toList
            
            if (records.isEmpty) datum else datum + (resultName -> records)
        })
    })
}