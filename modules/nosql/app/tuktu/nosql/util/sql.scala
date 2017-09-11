package tuktu.nosql.util

import java.sql.Connection
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import anorm._
import play.api.Play
import scala.Right

/**
 * Keeps track of connections
 */
object sql {
    case class ConnectionDefinition(
        url: String,
        user: String,
        password: String,
        driver: String)

    val pools = collection.mutable.Map[ConnectionDefinition, HikariDataSource]()
    // Gets a single connection
    def getConnection(conn: ConnectionDefinition,
            maxSize: Int = Play.current.configuration.getInt("tuktu.nosql.sql.pools.max_size").getOrElse(50)
    ): Connection = {
        def newConnection() = {
            // Create new source
            Class.forName(conn.driver)
            val config = new HikariConfig
            config.setJdbcUrl(conn.url)
            config.setUsername(conn.user)
            config.setPassword(conn.password)
            config.addDataSourceProperty("maximumPoolSize", maxSize)
            config.setLeakDetectionThreshold(30000)
            val ds = new HikariDataSource(config)
            pools += conn -> ds
            ds.getConnection
        }

        // Check if a pool exists
        if (pools.contains(conn)) pools(conn).getConnection
        else
            // Create the pool and return one
            newConnection
    }
    // Releases a connection, and closes its BasicDataSource if it has no more active connections
    def releaseConnection(connDef: ConnectionDefinition, conn: Connection) = conn.close

    /**
     * Turns an SQL row into a Map[String, Any]
     */
    def rowToMap(row: Row) = row.asMap.map(elem => elem._2 match {
        case e: Option[_] => elem._1 -> e.getOrElse("NULL")
        case e: Any       => elem
    })

    val parser: RowParser[Map[String, Any]] =
        SqlParser.folder(Map.empty[String, Any]) { (map, value, meta) =>
            Right(map + (meta.column.qualified -> value))
        }

    def queryResult(query: String, connDef: ConnectionDefinition, attempts: Int = 0)(implicit conn: Connection): (List[Map[String, Any]], Connection) =
        try {
            (SQL"#$query".as(parser.*), conn)
        } catch {
            case e: java.sql.SQLNonTransientConnectionException => {
                // Wait timeout passed?
                if (attempts < 3) queryResult(query, connDef, attempts + 1)
                else throw e
            }
            case e: java.sql.SQLException => {
                // Connection closed? Attempt to reopen
                if (conn.isClosed) {
                    queryResult(query, connDef, attempts)(getConnection(connDef))
                } else if (attempts < 3) queryResult(query, connDef, attempts + 1)
                    else throw e
            }
        }

    def query(query: String)(implicit conn: Connection) =
        SQL"#$query".execute

    def bulkQuery(query: String, parameters: List[NamedParameter])(implicit conn: Connection) =
        SQL(query)
            .on(parameters: _*)
            .executeUpdate
}