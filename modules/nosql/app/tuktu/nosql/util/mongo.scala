package tuktu.nosql.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.typesafe.config.Config
import akka.actor.Actor
import akka.actor.ActorRef
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

object MongoPool {
  case class getConn(nodes: List[String], mongoOptions: MongoConnectionOptions, auth: Option[Authenticate])
  case class releaseConn(nodes: List[String], connection: MongoConnection)
  
  class MongoPoolActor extends Actor {    
    case class graceKill(nodes: List[String], connection: MongoConnection)
    
    // Driver should be created only once since it's an entire actor system
    def typesafeConfig: com.typesafe.config.Config = play.api.libs.concurrent.Akka.system.settings.config
    val driver = new MongoDriver(Some(typesafeConfig))

    // Keep track of connections per replica set/cluster
    val nodesPerConnection = collection.concurrent.TrieMap.empty[List[String], collection.concurrent.TrieMap[MongoConnection, Int]]
    val maxLeases = 10

    def receive = {
      case c: getConn      => getConnection(c.nodes, c.mongoOptions, c.auth) pipeTo sender
      case c: releaseConn  => releaseConnection(c.nodes, c.connection)
      case c: graceKill => {
        val sNodes = c.nodes
        val connection = c.connection
        if (nodesPerConnection(sNodes)(connection) < 1) {
          // We are the last one, clean up
          nodesPerConnection(sNodes) -= connection

          connection.close
        } 
      }
    }

    def getConnection(nodes: List[String], mongoOptions: MongoConnectionOptions, auth: Option[Authenticate]): Future[MongoConnection] = {
      val sNodes = nodes.sorted

      def createConnection: Future[MongoConnection] = {
        // Create a new connection with a new lease
        val connection = driver.connection(nodes, mongoOptions)
        // Update nodes per connection
        nodesPerConnection(sNodes) += connection -> 1

        // Authenticate if required and return the connection
        auth match {
          case Some(a) => {
            val fut = connection.authenticate(a.db, a.user, a.password)
            // Wait for auth to succeed
            fut.map { _ => connection }
          }
          case None => Future { connection }
        }
      }

      // Check if we already have a lease for this one
      if (nodesPerConnection.contains(sNodes)) {
        // Get a new lease on an existing connection if we can still manage to; or create new connection
        nodesPerConnection(sNodes).find { case (_, leases) => leases < maxLeases } match {
          case None => createConnection
          case Some((connection, leases)) => {
            // Update lease counter
            nodesPerConnection(sNodes) += connection -> (leases + 1)
            Future{connection}            
          }
        }
      } else {
        // Initialize mapping and create connection
        nodesPerConnection += sNodes -> collection.concurrent.TrieMap.empty[MongoConnection, Int]
        createConnection
      }
    }

    def releaseConnection(nodes: List[String], connection: MongoConnection) {
      val sNodes = nodes.sorted

      // Get the lease
      this.synchronized {
        val leaseCount = nodesPerConnection(sNodes)(connection) - 1
        nodesPerConnection(sNodes) += connection -> (leaseCount)

        if (leaseCount < 1) {
          Akka.system.scheduler.scheduleOnce(timeout.duration) {
            self ! graceKill(sNodes, connection)
          }
        }
      }
    }
  }

  lazy val mongoPoolActor = Akka.system.actorOf(Props[MongoPoolActor])
  implicit val timeout = Timeout(30.seconds)

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
      case None => MongoConnectionOptions()
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
        })
    }
  }

  def getCollection(connection: MongoConnection, dbName: String, coll: String) = {
    // Get DB
    val db = connection.database(dbName)
    // Get collection
    db.map(_.collection(coll))
  }
}
