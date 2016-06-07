package tuktu.nosql.util

import akka.util.Timeout

import collection.immutable.SortedSet

import play.api.cache.Cache
import play.api.libs.json._
import play.api.Play.current
import play.modules.reactivemongo.json.collection._

import reactivemongo.api.AuthenticationMode
import reactivemongo.api.CrAuthentication
import reactivemongo.api.FailoverStrategy
import reactivemongo.api.MongoConnection
import reactivemongo.api.MongoConnectionOptions
import reactivemongo.api.MongoDriver
import reactivemongo.api.ScramSha1Authentication
import reactivemongo.core.nodeset.Authenticate

import scala.collection.mutable.HashMap
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory



case class MongoDBSettings(hosts: SortedSet[String], database: String, collection: String) 


object mongoTools
{
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)  
  
    def typesafeConfig: com.typesafe.config.Config = play.api.libs.concurrent.Akka.system.settings.config
    val driver = new reactivemongo.api.MongoDriver(Some(typesafeConfig))
    
    
    val connections = scala.collection.mutable.HashMap[SortedSet[String],MongoConnection]()
    val collections = scala.collection.mutable.HashMap[(MongoDBSettings,String),JSONCollection]()
    
    val strategy = FailoverStrategy(
      initialDelay = 500 milliseconds,
      retries = 5,
      delayFactor = attemptNumber => 1 + attemptNumber * 0.5
    )
    
    def getConnection(hosts: SortedSet[String], nbConn: Int): MongoConnection = 
    {
        connections.getOrElseUpdate(hosts, {
            driver.connection( hosts.toList, MongoConnectionOptions( nbChannelsPerNode = nbConn ) )
        })
    }
    
    def getConnection(hosts: SortedSet[String], scramsha1: Boolean, nbConn: Int): MongoConnection = 
    {
        connections.getOrElseUpdate(hosts, {
            val conOpts = scramsha1 match{
                case true => MongoConnectionOptions( authMode = ScramSha1Authentication, nbChannelsPerNode = nbConn )
                case false => MongoConnectionOptions( authMode = CrAuthentication, nbChannelsPerNode = nbConn )
            }
            driver.connection(hosts.toList, options = conOpts)
        })
    }
    
    
    def closeConnection(hosts: SortedSet[String]) =
    {
       connections.remove( hosts ) match {
            case Some( connection ) => connection.close()
       }
    }
    
    
    def getFutureCollection( settings: MongoDBSettings, nbChannelsPerNode: Int ): Future[JSONCollection] =
    {
        collections.get(settings,"") match{
          case Some(coll) => Future(coll)
          case None => {
            val connection = getConnection(settings.hosts, nbChannelsPerNode)
            val fdb = connection.database(settings.database, strategy)
            val fcoll = fdb.map(_.collection(settings.collection, strategy))
            fcoll.map{ coll => collections.put((settings,""), coll) }
            fcoll
          }
        }
    }
    
    def getFutureCollection( settings: MongoDBSettings, credentials: Authenticate, scramsha1: Boolean, nbChannelsPerNode: Int ): Future[JSONCollection] =
    {
        collections.get((settings,credentials.user)) match {
        case Some(coll) => Future(coll)
        case None => {
            val connection = getConnection(settings.hosts, scramsha1, nbChannelsPerNode)
            val fdb = connection.database(settings.database, strategy)
            // Authenticate
            val authdb = connection(credentials.db, strategy)
            val fauth = authdb.authenticate(credentials.user, credentials.password)(timeout.duration)
            //Thread.sleep(5000)
            val fcoll = fauth.flatMap{ auth => fdb.map{ db => db.collection(settings.collection, strategy) }}
            // store authenticated collection for future use 
            fcoll.map{ coll => collections.put((settings, credentials.user), coll) }
            fcoll
        }}
    }
    
    def deleteAllCollections =
    {
        collections.map( f => deleteCollectionForUser(f._1._1, f._1._2) )
    }
    
    def deleteCollection( settings: MongoDBSettings ) =
    {
        collections.remove( settings,"" ) 
        closeConnection( settings.hosts )
    }
    
    def deleteCollectionForUser( settings: MongoDBSettings, user: String ) =
    {
        collections.remove( (settings, user) ) 
        closeConnection( settings.hosts )
    }
}