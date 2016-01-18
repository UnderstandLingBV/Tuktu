package tuktu.nosql.util

import org.apache.commons.dbcp2.BasicDataSource
import java.sql.Connection
import anorm.NamedParameter
import anorm.SqlParser._
import anorm.Row
import anorm.SQL

/**
 * Keeps track of connections
 */
object sql {
    case class PoolCounter(
        pool: BasicDataSource,
        users: Int)

    case class ConnectionDefinition(
        url: String,
        user: String,
        password: String,
        driver: String)

    var pools = collection.mutable.Map[ConnectionDefinition, PoolCounter]()

    // Gets a single connection
    def getConnection(conn: ConnectionDefinition, minSize: Int = 5, maxSize: Int = 10) = {
        // Check if pool exists
        if (!pools.contains(conn)) {
            // Create connection
            val connectionPool = new BasicDataSource()
            connectionPool.setDriverClassName(conn.driver)
            connectionPool.setUrl(conn.url)
            connectionPool.setUsername(conn.user)
            connectionPool.setPassword(conn.password)
            connectionPool.setInitialSize(minSize)
            connectionPool.setMaxIdle(maxSize)

            // Add this pool
            pools += conn -> new PoolCounter(connectionPool, 0)
        }
        pools += conn -> new PoolCounter(pools(conn).pool, pools(conn).users + 1)

        // Return the damn thing
        pools(conn).pool.getConnection
    }

    // Relieve
    def releaseConnection(conn: ConnectionDefinition) = {
        if (pools.contains(conn)) {
            pools += conn -> new PoolCounter(pools(conn).pool, pools(conn).users - 1)
            if (pools(conn).users == 0) {
                pools(conn).pool.close
                pools -= conn
            }
        }
    }
    
    /**
     * Turns an SQL row into a Map[String, Any]
     */
    def rowToMap(row: Row) = row.asMap.map(elem => elem._2 match {
        case e: Option[_] => elem._1 -> e.getOrElse("NULL")
        case e: Any       => elem
    })

    /**
     * Query functions
     */
    def queryResult(query: String)(conn: Connection) =
        SQL(query).apply()(conn).toList

    def query(query: String)(conn: Connection) =
        SQL(query).execute()(conn)

    def bulkQuery(query: String, parameters: List[NamedParameter])(conn: Connection) =
        SQL(query)
            .on(parameters: _*)
            .executeUpdate()(conn)
}