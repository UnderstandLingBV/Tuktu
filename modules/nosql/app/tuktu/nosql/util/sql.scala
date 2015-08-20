package tuktu.nosql.util

import java.sql.DriverManager
import anorm._
import java.util.regex.Pattern

object sql {

    
    case class client(url: String, user: String, password: String, driver: String) {
        // Load the driver, set up the connection
        Class.forName(driver)
        val connection = DriverManager.getConnection(url, user, password)
        
        def queryResult(query: String) = SQL(query).apply()(connection).toList
        def query(query: String) = SQL(query).execute()(connection)
        
        def close() = {
            connection.close()
        }
    }
    
    /**
     * Turns an SQL row into a Map[String, Any]
     */
    def rowToMap(row: Row) = row.asMap.map(elem => elem._2 match {
        case e: Option[_] => elem._1 -> e.getOrElse("NULL")
        case e: Any => elem
    })
    

}