package tuktu.nosql.util

import java.sql.Connection
import java.util.UUID
import java.util.concurrent.Semaphore

import scala.Right
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global

import com.jolbox.bonecp.BoneCP
import com.jolbox.bonecp.BoneCPConfig

import anorm.Iteratees
import anorm.NamedParameter
import anorm.ParameterValue.toParameterValue
import anorm.Row
import anorm.RowParser
import anorm.SQL
import anorm.SqlParser
import anorm.SqlStringInterpolation
import anorm.sqlToSimple
import play.api.libs.iteratee.Enumerator
import akka.actor.Actor
import scala.util.Random
import play.api.Play
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariConfig

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
            maxSize: Int = Play.current.configuration.getInt("tuktu.nosql.sql.pools.min_size").getOrElse(50)
    ): Connection = {
        def newConnection() = {
            // Create new source
            Class.forName(conn.driver)
            val config = new HikariConfig
            config.setJdbcUrl(conn.url)
            config.setUsername(conn.user)
            config.setPassword(conn.password)
            config.addDataSourceProperty("maximumPoolSize", maxSize)
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
        SQL"#$query".as(parser.*)

    def query(query: String)(implicit conn: Connection) =
        SQL"#$query".execute

    def bulkQuery(query: String, parameters: List[NamedParameter])(implicit conn: Connection) =
        SQL(query)
            .on(parameters: _*)
            .executeUpdate
}