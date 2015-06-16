package tuktu.nosql.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils
import tuktu.nosql.util.sql._
import tuktu.nosql.util.stringHandler

class SQLProcessor(resultName: String) extends BaseProcessor(resultName) {
    var url = ""
    var user = ""
    var password = ""
    var driver = ""
    var append = false
    var query = ""
    
    var client: client = _
    
    override def initialize(config: JsObject) = {
        // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
        url = (config \ "url").as[String]
        user = (config \ "user").as[String]
        password = (config \ "password").as[String]
        driver = (config \ "driver").as[String]
        query = (config \ "query").as[String]
        
        // Append result or not?
        append = (config \ "append").asOpt[Boolean].getOrElse(false)
        
        // Set up the client
        client = new client(url, user, password, driver)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        new DataPacket(for (datum <- data.data) yield {
            // Evaluate all
            val evalQuery = stringHandler.evaluateString(query, datum)            
            val evalUrl = tuktu.api.utils.evaluateTuktuString(url, datum)
            val evalUser = tuktu.api.utils.evaluateTuktuString(user, datum)
            val evalPassword = tuktu.api.utils.evaluateTuktuString(password, datum)
            val evalDriver = tuktu.api.utils.evaluateTuktuString(driver, datum)
            
            // See if we need to update the client
            val reEvaluated = if (evalUrl != url || evalUser != user || evalPassword != password || evalDriver != driver) {
                client.close
                client = new client(evalUrl, evalUser, evalPassword, evalDriver)
                true
            } else false
            
            // See if we need to append the result
            append match {
                case false => {
                    // No need for appending
                    client.query(evalQuery)
                    datum
                }
                case true => {
                    // Get the result and use it
                    val res = client.queryResult(evalQuery).map(row => rowToMap(row))
                    datum + (resultName -> res)
                }
            }
        })
    }) compose Enumeratee.onEOF(() => client.close)
}