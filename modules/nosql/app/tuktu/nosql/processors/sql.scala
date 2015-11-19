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
    var query = ""
    var append = false
    var distinct = false

    var client: client = _

    override def initialize(config: JsObject) {
        // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
        url = (config \ "url").as[String]
        user = (config \ "user").as[String]
        password = (config \ "password").as[String]
        driver = (config \ "driver").as[String]
        query = (config \ "query").as[String]

        // Append result or not?
        append = (config \ "append").asOpt[Boolean].getOrElse(false)

        // Only query distinct setups
        distinct = (config \ "distinct").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        val query_results = collection.mutable.Map.empty[(String, String, String, String, String), List[Map[String, Any]]]

        val results = for (datum <- data.data) yield {
            // Evaluate all
            val evalQuery = stringHandler.evaluateString(query, datum)
            val evalUrl = tuktu.api.utils.evaluateTuktuString(url, datum)
            val evalUser = tuktu.api.utils.evaluateTuktuString(user, datum)
            val evalPassword = tuktu.api.utils.evaluateTuktuString(password, datum)
            val evalDriver = tuktu.api.utils.evaluateTuktuString(driver, datum)

            // If we only execute distinctly, skip if we already have a query result for these exact settings
            if (distinct && query_results.contains((evalQuery, evalUrl, evalUser, evalPassword, evalDriver)))
                query_results((evalQuery, evalUrl, evalUser, evalPassword, evalDriver))
            else {
                // See if we need to initialize client
                if (client == null)
                    client = new client(evalUrl, evalUser, evalPassword, evalDriver)
                // See if we need to update the client
                else if (evalUrl != client.url || evalUser != client.user || evalPassword != client.password || evalDriver != client.driver) {
                    client.close
                    client = new client(evalUrl, evalUser, evalPassword, evalDriver)
                }
                // See if we need to append or not
                if (append) {
                    client.query(evalQuery)
                    query_results += (evalQuery, evalUrl, evalUser, evalPassword, evalDriver) -> Nil
                    Nil
                } else {
                    val res = client.queryResult(evalQuery).map(row => rowToMap(row))
                    query_results += (evalQuery, evalUrl, evalUser, evalPassword, evalDriver) -> res
                    res
                }
            }
        }

        // If we have to append data, zip datums with query results, otherwise return DataPacket untouched
        if (append) {
            new DataPacket(data.data.zip(results).map(tuple => tuple._1 + (resultName -> tuple._2)))
        } else {
            data
        }
    }) compose Enumeratee.onEOF(() => client.close)
}