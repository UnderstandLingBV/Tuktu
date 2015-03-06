package tuktu.nosql.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils
import tuktu.nosql.util.sql

class SQLProcessor(resultName: String) extends BaseProcessor(resultName) {
    var client: sql.client = null
    var append = false
    var query = ""
    
    override def initialize(config: JsObject) = {
        // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
        val url = (config \ "url").as[String]
        val user = (config \ "user").as[String]
        val password = (config \ "password").as[String]
        val driver = (config \ "driver").as[String]
        query = (config \ "query").as[String]
        
        // Set up the client
        client = new sql.client(url, user, password, driver)
        
        // Append result or not?
        append = (config \ "append").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        Future {new DataPacket(for (datum <- data.data) yield {
            // Evaluate query
            val evalQuery = utils.evaluateTuktuString(query, datum)
            
            // See if we need to append the result
            append match {
                case false => {
                    // No need for appending
                    client.query(query)
                    datum
                }
                case true => {
                    // Get the result and use it
                    val res = client.queryResult(query).map(row => sql.rowToMap(row))
                    
                    datum + (resultName -> res)
                }
            }
        })}
    }) compose Enumeratee.onEOF(() => {
        client.close
    })
}