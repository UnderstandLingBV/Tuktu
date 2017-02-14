package tuktu.nosql.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.typesafe.config.Config
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Props
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsObject
import play.modules.reactivemongo.json.collection._
import reactivemongo.api.CrAuthentication
import reactivemongo.api.MongoConnection
import reactivemongo.api.MongoConnectionOptions
import reactivemongo.api.MongoDriver
import reactivemongo.api.ReadPreference
import reactivemongo.api.ScramSha1Authentication
import reactivemongo.api.commands.WriteConcern
import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.duration._
import reactivemongo.api.FailoverStrategy

object MongoPool {
    // Driver should be created only once since it's an entire actor system
    def typesafeConfig: com.typesafe.config.Config = play.api.libs.concurrent.Akka.system.settings.config
    val driver = new MongoDriver(Some(typesafeConfig))
    val pools = collection.mutable.Map[List[String], MongoConnection]()

    /**
     * Gets a MongoConnection for a specific replica set, reusing existing ones
     */
    def getConnection(nodes: List[String], mongoOptions: MongoConnectionOptions, auth: Option[Authenticate]): Future[MongoConnection] = {
        def newConnection() = {
            // Create a new connection with a new lease and add it to our map
            val connection = driver.connection(nodes, mongoOptions)

            // Authenticate if required and return the connection
            auth match {
                case Some(a) =>
                    val fut = connection.authenticate(a.db, a.user, a.password)
                    // Wait for auth to succeed
                    fut.map {_ =>
                        // Add to pool
                        pools += nodes -> connection
                        connection
                    }
                case None => Future {
                    // Add to pool
                    pools += nodes -> connection
                    connection
                }
            }
        }

        // Check if a pool exists
        if (pools.contains(nodes)) Future { pools(nodes) }
        else
            // Create the pool and return one
            newConnection
    }

    /**
     * Releases a mongo connection lease
     */
    def releaseConnection(nodes: List[String], connection: MongoConnection) {
    }

    /**
     * Parses a JsObject into MongoConnectionOptions
     */
    def parseMongoOptions(opts: Option[JsObject]) = {
        opts match {
            case None => MongoConnectionOptions(
                failoverStrategy = FailoverStrategy(
                    retries = 50,
                    delayFactor = n => n * 1.1))
            case Some(o) => MongoConnectionOptions(
                connectTimeoutMS = (o \ "connectTimeoutMS").asOpt[Int] match {
                    case None    => 0
                    case Some(a) => a
                },
                authSource = (o \ "authSource").asOpt[String] match {
                    case None    => None
                    case Some(a) => Some(a)
                },
                sslEnabled = (o \ "sslEnabled").asOpt[Boolean] match {
                    case None    => false
                    case Some(a) => a
                },
                sslAllowsInvalidCert = (o \ "sslAllowsInvalidCert").asOpt[Boolean] match {
                    case None    => false
                    case Some(a) => a
                },
                authMode = (o \ "authMode").asOpt[String] match {
                    case Some(a) if a.toLowerCase == "sha1" || a.toLowerCase == "scramsha1" ||
                        a.toLowerCase == "scramsha" || a.toLowerCase == "scram" || a.toLowerCase == "sha" => ScramSha1Authentication
                    case _ => CrAuthentication
                },
                tcpNoDelay = (o \ "tcpNoDelay").asOpt[Boolean] match {
                    case None    => false
                    case Some(a) => a
                },
                keepAlive = (o \ "keepAlive").asOpt[Boolean] match {
                    case None    => false
                    case Some(a) => a
                },
                nbChannelsPerNode = (o \ "nbChannelsPerNode").asOpt[Int] match {
                    case None    => 10
                    case Some(a) => a
                },
                writeConcern = (o \ "writeConcern").asOpt[String] match {
                    case Some(a) if a.toLowerCase == "acknowledged" => WriteConcern.Acknowledged
                    case Some(a) if a.toLowerCase == "journaled" => WriteConcern.Journaled
                    case Some(a) if a.toLowerCase == "unacknowledged" => WriteConcern.Unacknowledged
                    case _ => WriteConcern.Default
                },
                readPreference = (o \ "readPreference").asOpt[String] match {
                    case Some(a) if a.toLowerCase == "primarypreferred" => ReadPreference.primaryPreferred
                    case Some(a) if a.toLowerCase == "secondary" => ReadPreference.secondary
                    case Some(a) if a.toLowerCase == "secondarypreferred" => ReadPreference.secondaryPreferred
                    case Some(a) if a.toLowerCase == "nearest" => ReadPreference.nearest
                    case _ => ReadPreference.primary
                },
                failoverStrategy = FailoverStrategy(
                    retries = 50,
                    delayFactor = n => n * 1.1))
        }
    }

    def getCollection(connection: MongoConnection, dbName: String, coll: String) = {
        // Get DB
        val db = connection.database(dbName)
        // Get collection
        db.map(_.collection(coll))
    }
}