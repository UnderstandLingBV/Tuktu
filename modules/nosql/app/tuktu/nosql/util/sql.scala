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
import scala.collection.mutable.HashSet
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

    val pools = TrieMap[ConnectionDefinition, HashSet[BasicDataSource]]()

    // Gets a single connection
    def getConnection(conn: ConnectionDefinition, minSize: Int = 5, maxSize: Int = 10): Connection = {
        // Check if a pool exists which still has open slots
        pools.get(conn).flatMap { map =>
            map.find { basicDataSource => basicDataSource.getNumActive < minSize }
        } match {
            case Some(basicDataSource) => {
                if (basicDataSource.isClosed) {
                    // It's closed, remove it and try again
                    pools.get(conn).collect { case set => set -= basicDataSource }
                    getConnection(conn, minSize, maxSize)
                } else
                    basicDataSource.getConnection
            }
            case None => {
                // Create new source
                val connectionPool = new BasicDataSource()
                connectionPool.setDriverClassName(conn.driver)
                connectionPool.setUrl(conn.url)
                connectionPool.setUsername(conn.user)
                connectionPool.setPassword(conn.password)
                connectionPool.setInitialSize(minSize)
                connectionPool.setMaxIdle(maxSize)
                pools.getOrElseUpdate(conn, HashSet.empty) += connectionPool
                connectionPool.getConnection
            }
        }
    }

    // Releases a connection, and closes its BasicDataSource if it has no more active connections
    def releaseConnection(connDef: ConnectionDefinition, conn: Connection) = {
        conn.close
        pools.get(connDef).collect {
            case set =>
                set.foreach { basicDataSource =>
                    // Check if BasicDataSource has no active connections
                    if (basicDataSource.getNumActive == 0) {
                        // Close it and remove it from set
                        set -= basicDataSource
                        basicDataSource.close
                    }
                }
                if (set.isEmpty)
                    pools -= connDef
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