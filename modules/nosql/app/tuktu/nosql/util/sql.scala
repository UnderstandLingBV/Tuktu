package tuktu.nosql.util

import java.sql.Connection
import anorm.SqlParser._
import anorm._
import play.api.libs.iteratee.Enumerator
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.HashSet
import com.jolbox.bonecp.BoneCP
import com.jolbox.bonecp.BoneCPConfig

/**
 * Keeps track of connections
 */
object sql {
    case class ConnectionDefinition(
        url: String,
        user: String,
        password: String,
        driver: String)
    
    val pools = TrieMap[ConnectionDefinition, HashSet[BoneCP]]()

    // Gets a single connection
    def getConnection(conn: ConnectionDefinition, minSize: Int = 5, maxSize: Int = 10): Connection = {
        // Check if a pool exists which still has open slots
        pools.get(conn).flatMap { map =>
            map.find { cp => cp.getTotalFree > 0 }
        } match {
            case Some(cp) => cp.getConnection
            case None => {
                // Create new source
                Class.forName(conn.driver)
                val config = new BoneCPConfig()
                config.setJdbcUrl(conn.url)
                config.setUsername(conn.user)
                config.setPassword(conn.password)
                config.setMinConnectionsPerPartition(minSize)
                config.setMaxConnectionsPerPartition(maxSize)
                config.setPartitionCount(3)
                config.setCloseConnectionWatch(true)
                val c = new BoneCP(config)
                pools.getOrElseUpdate(conn, HashSet.empty) += c
                c.getConnection
            }
        }
    }

    // Releases a connection, and closes its BasicDataSource if it has no more active connections
    def releaseConnection(connDef: ConnectionDefinition, conn: Connection) = {
        if (!conn.isClosed) conn.close
        
        // Need to clean up our pool?
        pools.get(connDef).collect {
            case set =>
                set.foreach { cp =>
                    // Check if BasicDataSource has no active connections
                    if (cp.getTotalLeased == 0) {
                        // Close Remove it from out set
                        set -= cp
                        cp.close
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
        SQL"#$query".as(parser.*)

    def query(query: String)(implicit conn: Connection) =
        SQL"#$query".execute

    def bulkQuery(query: String, parameters: List[NamedParameter])(implicit conn: Connection) =
        SQL(query)
            .on(parameters: _*)
            .executeUpdate
}