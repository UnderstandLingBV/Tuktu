package tuktu.nosql.util

import java.sql.DriverManager
import anorm._

object sql {
    case class client(url: String, user: String, password: String, driver: String) {
        // Load the driver, set up the connection
        Class.forName(driver)
        implicit val connection = DriverManager.getConnection(url, user, password)

        def queryResult(query: String): List[Row] = SQL(query).apply.toList
        def query(query: String): Boolean = SQL(query).execute

        def close = connection.close
    }

    /**
     * Turns an SQL row into a Map[String, Any]
     */
    def rowToMap(row: Row): Map[String, Any] = row.asMap.mapValues(value => value match {
        case e: Option[_] => e.getOrElse("NULL")
        case e: Any       => e
    })
}