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

class CacheTableProcessor(resultName: String) extends BaseProcessor(resultName) {
    val tableData = collection.mutable.Map.empty[String, List[Map[String, Any]]]
    var dataQuery: JsObject = _
    
    override def initialize(config: JsObject) {
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
        tableData ++= results._1.groupBy(_((config \ "key_by").as[String]).toString)
        // Query
        dataQuery = (config \ "data_query").as[JsObject]
        
        // Release
        releaseConnection(connDef, conn)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        new DataPacket(data.data.map {datum =>
            val t = utils.evaluateTuktuString((dataQuery \ "type").as[String], datum)
            val query = utils.evaluateTuktuString((dataQuery \ "query").as[String], datum)
            
            val records = ((t, query) match {
                case ("starts_with", q) => tableData.collect {
                    case row if (row._1.endsWith(".*") && row._1.startsWith(q)) || row._1 == q => row._2
                }
                case (_, q) => tableData.collect {
                    case row if row._1 == q => row._2
                }
            }).flatten.toList
            
            if (records.isEmpty) datum else datum + (resultName -> records)
        })
    })
}