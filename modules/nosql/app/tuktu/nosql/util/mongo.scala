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
    case class getConn(nodes: List[String], mongoOptions: MongoConnectionOptions, auth: Option[Authenticate])
    case class releaseConn(nodes: List[String], connection: MongoConnection)
    case class graceKill(nodes: List[String], connection: MongoConnection)
    case class MongoConnectionObject(var leases: Int = 1, var graceKill: Option[Cancellable] = None)

    class MongoPoolActor extends Actor {
        // Driver should be created only once since it's an entire actor system
        def typesafeConfig: com.typesafe.config.Config = play.api.libs.concurrent.Akka.system.settings.config
        val driver = new MongoDriver(Some(typesafeConfig))

        // Keep track of connections per replica set/cluster
        val nodesPerConnection = collection.concurrent.TrieMap.empty[List[String], collection.concurrent.TrieMap[MongoConnection, MongoConnectionObject]]
        val maxLeases = 10

        def receive = {
            case getConn(nodes, opts, auth)     => getConnection(nodes, opts, auth) pipeTo sender
            case releaseConn(nodes, connection) => releaseConnection(nodes, connection)
            case graceKill(sNodes, connection) =>
                if (nodesPerConnection.contains(sNodes))
                    if (nodesPerConnection(sNodes).contains(connection))
                        if (nodesPerConnection(sNodes)(connection).leases < 1) {
                            // We are the last one, clean up
                            nodesPerConnection(sNodes) -= connection
                            if (nodesPerConnection(sNodes).isEmpty) nodesPerConnection -= sNodes

                            connection.close
                        }
        }

        def getConnection(nodes: List[String], mongoOptions: MongoConnectionOptions, auth: Option[Authenticate]): Future[MongoConnection] = {
            val sNodes = nodes.sorted

            def createConnection: Future[MongoConnection] = {
                // Create a new connection with a new lease and add it to our map
                val connection = driver.connection(nodes, mongoOptions)
                // Update nodes per connection
                nodesPerConnection(sNodes) += connection -> new MongoConnectionObject

                // Authenticate if required and return the connection
                auth match {
                    case Some(a) =>
                        val fut = connection.authenticate(a.db, a.user, a.password)
                        // Wait for auth to succeed
                        fut.map { _ => connection }

                    case None => Future { connection }
                }
            }

            // Check if we already have a lease for this one
            if (nodesPerConnection.contains(sNodes)) {
                // Get a new lease on an existing connection if we can still manage to (prefer already leased connections); or create new connection
                nodesPerConnection(sNodes).find { case (_, MongoConnectionObject(leases, _)) => leases < maxLeases && leases > 0 } match {
                    case None =>
                        // No free already leased connections; check if there's empty connections
                        nodesPerConnection(sNodes).find { case (_, MongoConnectionObject(leases, _)) => leases == 0 } match {
                            // Nothing is available: create new connection
                            case None => createConnection
                            case Some((connection, mco: MongoConnectionObject)) =>
                                // Update lease counter
                                mco.leases += 1
                                // Connection is in use: cancel graceKill
                                mco.graceKill.foreach { _.cancel }
                                mco.graceKill = None
                                Future { connection }
                        }
                    case Some((connection, mco: MongoConnectionObject)) =>
                        // Update lease counter
                        mco.leases += 1
                        // Connection is in use: cancel graceKill
                        mco.graceKill.foreach { _.cancel }
                        mco.graceKill = None
                        Future { connection }
                }
            } else {
                // Initialize mapping and create connection
                nodesPerConnection += sNodes -> collection.concurrent.TrieMap.empty[MongoConnection, MongoConnectionObject]
                createConnection
            }
        }

        def releaseConnection(nodes: List[String], connection: MongoConnection) {
            val sNodes = nodes.sorted
            val mco = nodesPerConnection(sNodes)(connection)

            // Update lease count
            mco.leases -= 1
            // Kill connection gracefully if required
            if (mco.leases < 1) {
                mco.graceKill.foreach { _.cancel }
                mco.graceKill = Some(Akka.system.scheduler.scheduleOnce(timeout.duration) {
                    self ! graceKill(sNodes, connection)
                })
            }
        }
    }

    lazy val mongoPoolActor = Akka.system.actorOf(Props[MongoPoolActor])
    implicit val timeout = Timeout(30 seconds)

    /**
     * Gets a MongoConnection for a specific replica set, reusing existing ones
     */
    def getConnection(nodes: List[String], mongoOptions: MongoConnectionOptions, auth: Option[Authenticate]): Future[MongoConnection] = {
        (mongoPoolActor ? getConn(nodes, mongoOptions, auth)).mapTo[MongoConnection]
    }

    /**
     * Releases a mongo connection lease
     */
    def releaseConnection(nodes: List[String], connection: MongoConnection) {
        mongoPoolActor ! releaseConn(nodes, connection)
    }

    /**
     * Parses a JsObject into MongoConnectionOptions
     */
    def parseMongoOptions(opts: Option[JsObject]) = {
        opts match {
            case None => MongoConnectionOptions(
                failoverStrategy = FailoverStrategy(
                    retries = 8,
                    delayFactor = n => n * 1.2))
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