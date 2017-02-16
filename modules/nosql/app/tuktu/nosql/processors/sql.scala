package tuktu.nosql.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.{ BaseProcessor, DataPacket }
import tuktu.nosql.util.sql._
import java.sql.Connection

class SQLProcessor(resultName: String) extends BaseProcessor(resultName) {
    var url: String = _
    var user: String = _
    var password: String = _
    var driver: String = _
    var query: String = _
    var append: Boolean = _
    var separate: Boolean = _
    var distinct: Boolean = _
    var clearOnEmpty: Boolean = _

    var connDef: ConnectionDefinition = null
    var conn: Connection = null

    override def initialize(config: JsObject) {
        // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
        url = (config \ "url").as[String]
        user = (config \ "user").as[String]
        password = (config \ "password").as[String]
        driver = (config \ "driver").as[String]
        query = (config \ "query").as[String]

        // Append result or not?
        append = (config \ "append").asOpt[Boolean].getOrElse(false)

        // Seperate datum for each result row?
        separate = (config \ "separate").asOpt[Boolean].getOrElse(true)

        // Only query distinct setups
        distinct = (config \ "distinct").asOpt[Boolean].getOrElse(false)
        
        // If we get an empty query, should we ditch the datum?
        clearOnEmpty = (config \ "clear_on_empty").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        val query_results = collection.mutable.Map.empty[(String, String, String, String, String), List[Map[String, Any]]]

        val results = for (datum <- data.data) yield {
            // Evaluate all
            val evalQuery = tuktu.api.utils.evaluateTuktuString(query, datum)
            val evalUrl = tuktu.api.utils.evaluateTuktuString(url, datum)
            val evalUser = tuktu.api.utils.evaluateTuktuString(user, datum)
            val evalPassword = tuktu.api.utils.evaluateTuktuString(password, datum)
            val evalDriver = tuktu.api.utils.evaluateTuktuString(driver, datum)

            // If we only execute distinctly, skip if we already have a query result for these exact settings
            if (distinct && query_results.contains((evalQuery, evalUrl, evalUser, evalPassword, evalDriver)))
                query_results((evalQuery, evalUrl, evalUser, evalPassword, evalDriver))
            else {
                // Initialize
                if (connDef == null) {
                    connDef = new ConnectionDefinition(evalUrl, evalUser, evalPassword, evalDriver)
                    // Get connection from pool
                    conn = getConnection(connDef)
                }

                // Check change
                if (connDef.url != evalUrl || connDef.user != evalUser || connDef.password != evalPassword || connDef.driver != evalDriver) {
                    // Give back
                    releaseConnection(connDef, conn)
                    connDef = new ConnectionDefinition(evalUrl, evalUser, evalPassword, evalDriver)
                    // Get connection from pool
                    conn = getConnection(connDef)
                }

                // See if we need to append or not
                if (append) {
                    val res = queryResult(evalQuery)(conn)
                    // Add to query results, only if distinct
                    if (distinct)
                        query_results += (evalQuery, evalUrl, evalUser, evalPassword, evalDriver) -> res
                    res
                } else {
                    tuktu.nosql.util.sql.query(evalQuery)(conn)
                    // Add to query results, only if distinct
                    if (distinct)
                        query_results += (evalQuery, evalUrl, evalUser, evalPassword, evalDriver) -> Nil
                    Nil
                }
            }
        }

        // If we have to append data, zip datums with query results, otherwise return DataPacket untouched
        if (append) {
            if (separate)
                DataPacket(data.data.zip(results).flatMap(tuple => {
                    // What if the result is empty?
                    if (tuple._2.isEmpty) 
                        if (clearOnEmpty) List()
                        else {
                            // The result is empty, so return the original DP
                            List(tuple._1)
                        }
                    else tuple._2.map(row => tuple._1 + (resultName -> row))
                }))
            else DataPacket(data.data.zip(results).map(tuple => tuple._1 + (resultName -> tuple._2)))
        } else data
    }) compose Enumeratee.onEOF(() => if (connDef != null) releaseConnection(connDef, conn))
}