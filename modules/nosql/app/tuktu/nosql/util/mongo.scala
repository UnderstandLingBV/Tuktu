package tuktu.nosql.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.typesafe.config.Config

import play.api.Play
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
import play.api.Play.current

object MongoPool {
    // Driver should be created only once since it's an entire actor system
    def typesafeConfig: com.typesafe.config.Config = play.api.libs.concurrent.Akka.system.settings.config
    val driver = new MongoDriver(Some(typesafeConfig))
    
    // Keep track of connections per replica set/cluster
    val nodesPerConnection = collection.mutable.Map.empty[List[String], collection.mutable.Map[MongoConnection, Int]]
    val maxLeases = 10

    /**
     * Gets a MongoConnection for a specific replica set, reusing existing ones
     */
    def getConnection(nodes: List[String], mongoOptions: MongoConnectionOptions, auth: Option[Authenticate]): Future[MongoConnection] = {
        val sNodes = nodes.sorted
        def createConnection() = {
            // Create a new connection with a new lease
            val connection = driver.connection(nodes, mongoOptions)
            // Update nodes per connection
            nodesPerConnection(sNodes) += connection -> 1
            
            // Authenticate if required and return the connection
            auth match {
                case Some(a) => {
                    val fut = connection.authenticate(a.db, a.user, a.password)
                    // Wait for auth to succeed
                    fut.map { _ => {
                        connection
                    }}
                }
                case None => Future { connection }
            }
        }
        
        // Check if we already have a lease for this one
        if (nodesPerConnection.contains(sNodes)) {
            // Get a new lease if we can still manage to
            val availableLeases = nodesPerConnection(sNodes).filter(_._2 < 10)
            if (availableLeases.size > 0) {
                Future {
                    // Use existing connection with a new lease
                    val connection = availableLeases.head
                    // Update lease counter
                    nodesPerConnection(sNodes) += connection._1 -> (connection._2 + 1)
                    connection._1
                }
            }
            else createConnection
        } else {
            // Initialize mapping and create connection
            nodesPerConnection += sNodes -> collection.mutable.Map.empty[MongoConnection, Int]
            createConnection
        }
    }
    
    /**
     * Releases a mongo connection lease
     */
    def releaseConnection(nodes: List[String], connection: MongoConnection) = {
        val sNodes = nodes.sorted
        
        // Get the lease
        val leaseCount = nodesPerConnection(sNodes)(connection)
        if (leaseCount == 1) {
            // We are the last one, clean up
            nodesPerConnection(sNodes) -= connection
            connection.close
        }
        else
            // Just decrease the counter
            nodesPerConnection(sNodes) += connection -> (leaseCount - 1)
    }
    
    /**
     * Parses a JsObject into MongoConnectionOptions
     */
    def parseMongoOptions(opts: Option[JsObject]) = {
        opts match {
            case None => MongoConnectionOptions()
            case Some(o) => MongoConnectionOptions(
                    connectTimeoutMS = (o \ "connectTimeoutMS").asOpt[Int] match {
                        case None => 0
                        case Some(a) => a
                    },
                    authSource = (o \ "authSource").asOpt[String] match {
                        case None => None
                        case Some(a) => Some(a)
                    },
                    sslEnabled = (o \ "sslEnabled").asOpt[Boolean] match {
                        case None => false
                        case Some(a) => a
                    },
                    sslAllowsInvalidCert = (o \ "sslAllowsInvalidCert").asOpt[Boolean] match {
                        case None => false
                        case Some(a) => a
                    },
                    authMode = (o \ "authMode").asOpt[String] match {
                        case Some(a) if a.toLowerCase == "sha1" || a.toLowerCase == "scramsha1" ||
                            a.toLowerCase == "scramsha" || a.toLowerCase == "scram" || a.toLowerCase == "sha" => ScramSha1Authentication
                        case _ => CrAuthentication
                    },
                    tcpNoDelay = (o \ "tcpNoDelay").asOpt[Boolean] match {
                        case None => false
                        case Some(a) => a
                    },
                    keepAlive = (o \ "keepAlive").asOpt[Boolean] match {
                        case None => false
                        case Some(a) => a
                    },
                    nbChannelsPerNode = (o \ "nbChannelsPerNode").asOpt[Int] match {
                        case None => 10
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
                    }
            )
        }
    }
    
    def getCollection(connection: MongoConnection, dbName: String, coll: String) = {
        // Get DB
        val db = connection.database(dbName)
        // Get collection
        db.map(_.collection(coll))
    }
}