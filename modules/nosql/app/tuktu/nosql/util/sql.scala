package tuktu.nosql.util

import java.sql.DriverManager
import anorm._

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
    
    def rowToMap(row: Row) = row.asMap
}