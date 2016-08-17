package tuktu.nosql.util

import org.apache.commons.dbcp2.BasicDataSource
import java.sql.Connection
import anorm.NamedParameter
import anorm.SqlParser._
import anorm.Row
import anorm.SQL
import anorm.SqlParser
import anorm.Iteratees
import play.api.libs.iteratee.Enumerator
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.concurrent.TrieMap
import anorm.RowParser

/**
 * Keeps track of connections
 */
object sql {
    case class ConnectionDefinition(
        url: String,
        user: String,
        password: String,
        driver: String)

    var pools = TrieMap[ConnectionDefinition, TrieMap[BasicDataSource, Int]]()

    // Gets a single connection
    def getConnection(conn: ConnectionDefinition, minSize: Int = 5, maxSize: Int = 10) = {
        // Check if a pool exists which still has open slots
        pools.get(conn).flatMap { map =>
            map.find { case (basicDataSource, _) => basicDataSource.getNumActive < minSize }
        } match {
            case Some((basicDataSource, _)) => basicDataSource.getConnection
            case None => {
                // Create new source
                val connectionPool = new BasicDataSource()
                connectionPool.setDriverClassName(conn.driver)
                connectionPool.setUrl(conn.url)
                connectionPool.setUsername(conn.user)
                connectionPool.setPassword(conn.password)
                connectionPool.setInitialSize(minSize)
                connectionPool.setMaxIdle(maxSize)
                pools.getOrElseUpdate(conn, TrieMap()) += connectionPool -> 1
                connectionPool.getConnection
            }
        }
    }

    // Releases a connection, and closes its BasicDataSource if it has no more active connections
    def releaseConnection(connDef: ConnectionDefinition, conn: Connection) = {
        conn.close
        pools.get(connDef).collect {
            case map =>
                map.foreach {
                    case (basicDataSource, _) =>
                        // Check if BasicDataSource has no active connections
                        if (basicDataSource.getNumActive == 0) {
                            // Close it and remove it from map
                            basicDataSource.close
                            map -= basicDataSource
                        }
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
    def streamResult(query: String)(implicit conn: Connection): Enumerator[Row] =
        Iteratees.from(SQL(query))

    val parser: RowParser[Map[String, Any]] =
        SqlParser.folder(Map.empty[String, Any]) { (map, value, meta) =>
            Right(map + (meta.column.qualified -> value))
        }

    def queryResult(query: String)(implicit conn: Connection) =
        SQL(query).as(parser.*)

    def query(query: String)(conn: Connection) =
        SQL(query).execute()(conn)

    def bulkQuery(query: String, parameters: List[NamedParameter])(conn: Connection) =
        SQL(query)
            .on(parameters: _*)
            .executeUpdate()(conn)
}