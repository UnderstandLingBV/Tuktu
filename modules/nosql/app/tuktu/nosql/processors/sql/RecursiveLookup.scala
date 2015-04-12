package tuktu.nosql.processors.sql

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils
import tuktu.nosql.util.sql

/**
 * In SQL, an edge table contains edges from a graph and relations need to be fetched iteratively.
 * This processor does just that.
 */
class RecursiveLookupProcessor(resultName: String) extends BaseProcessor(resultName) {
    var client: sql.client = null
    
    // Column to fetch
    var fetchColumns = Map[String, String]()
    // From-clause
    var fromClause = ""
    // Where-clause
    var whereClause = ""
    
    var includeOriginal = false
    var iterationDepth: Option[Int] = None
    
    override def initialize(config: JsObject) = {
        // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
        val url = (config \ "url").as[String]
        val user = (config \ "user").as[String]
        val password = (config \ "password").as[String]
        val driver = (config \ "driver").as[String]
        
        // The columns-field contains the columns to fetch from DB and the mapping to vars in the WHERE-body 
        fetchColumns = {
            (for (column <- (config \ "columns").as[List[JsObject]]) yield {
                // Get the name of the column and the mapping to variable
                (
                    (column \ "name").as[String],
                    (column \ "var").as[String]
                )
            }).toMap
        }
        // Get the from-clause and where-clause
        fromClause = (config \ "from").as[String]
        whereClause = (config \ "where").as[String]
        
        // Keep or discard original record?
        includeOriginal = (config \ "include_original").asOpt[Boolean].getOrElse(false)
        // Do we stop or find exhaustively?
        iterationDepth = (config \ "n").asOpt[Int]
        
        // Set up the client
        client = new sql.client(url, user, password, driver)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket((for (datum <- data.data) yield {
                // Get the ancestors
                val ancestors = recQueryFetcher(datum, 0)
                
                // Return all of them
                {if (includeOriginal) List(datum) else List()} ++
                    ancestors.map(ancestor => {
                        datum ++ ancestor
                    })
            }).flatten
        )
    })
    
    /**
     * This function recursively finds all ancestors until there are no more, no cycle detection here though!
     */
    def recQueryFetcher(datum: Map[String, Any], iteration: Int): List[Map[String, Any]] = iterationDepth match {
        case Some(depth) if iteration >= depth => List()
        case _ => {
            // Build and evaluate the query for this datum
            val query = {
                "SELECT " + fetchColumns.keys.mkString(",") +
                    " FROM " + sql.evaluateSqlString(fromClause, datum) +
                    " WHERE " + sql.evaluateSqlString(whereClause, datum)
            }
            
            // Get the result and use it to fetch new relations
            val parents = client.queryResult(query).map(row => {
                val map = sql.rowToMap(row)
                
                // Replace the anorm table naming with what we need
                map.map(elem => {
                    // Find the column name that matches and map to variable
                    fetchColumns(elem._1) -> elem._2
                })
            })
            
            // For each of these results, we need to do the same thing
            val grandParents = for (parent <- parents) yield recQueryFetcher(parent, iteration + 1)
            
            // Combine all
            parents ++ grandParents.flatten
        }
    }
}