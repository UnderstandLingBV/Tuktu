package tuktu.nosql.util

import akka.util.Timeout

import play.api.cache.Cache
import play.api.libs.json._
import play.api.Play.current
import play.modules.reactivemongo.json.collection._

import reactivemongo.api.AuthenticationMode
import reactivemongo.api.CrAuthentication
import reactivemongo.api.MongoConnection
import reactivemongo.api.MongoConnectionOptions
import reactivemongo.api.MongoDriver
import reactivemongo.api.ScramSha1Authentication
import reactivemongo.core.nodeset.Authenticate

import scala.collection.mutable.HashMap
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MongoTools 
{
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    def typesafeConfig: com.typesafe.config.Config = play.api.libs.concurrent.Akka.system.settings.config
    val driver = new reactivemongo.api.MongoDriver(Some(typesafeConfig))
    
    val connections = scala.collection.mutable.HashMap[Any,MongoConnection]()
    val collections = scala.collection.mutable.HashMap[(Any,MongoSettings),Future[JSONCollection]]()
    
    def getConnection(genproc: Any, hosts: List[String]): MongoConnection = 
    {
        connections.getOrElseUpdate(genproc, {
            driver.connection( hosts )
        })
    }
    
    def getConnection(genproc: Any, hosts: List[String], scramsha1: Boolean): MongoConnection = 
    {
        connections.getOrElseUpdate(genproc, {
            val conOpts = scramsha1 match{
                case true => MongoConnectionOptions( authMode = ScramSha1Authentication )
                case false => MongoConnectionOptions( authMode = CrAuthentication )
            }
            driver.connection(hosts, options = conOpts)
        })
    }
    
    
    def closeConnection(genproc: Any) =
    {
        connections.remove( genproc ) match {
            case Some( connection ) => connection.close()
        }
    }
    
    def getFutureCollection( genproc: Any, settings: MongoSettings ): Future[JSONCollection] =
    {
        collections.getOrElseUpdate( (genproc,settings), {
            Future{
                val connection = getConnection(genproc, settings.hosts)
                val db = connection(settings.database)
                db(settings.collection)
            }
        })
    }
    
    def getFutureCollection( genproc: Any, settings: MongoSettings, credentials: Authenticate, scramsha1: Boolean ): Future[JSONCollection] =
    {
        collections.getOrElseUpdate( (genproc,settings), {
            val connection = getConnection(genproc, settings.hosts, scramsha1)
            // Authenticate
            val db = connection(settings.database)
            val auth = (credentials.db.equals( settings.database )) match {
                case true => db.authenticate(credentials.user, credentials.password)(timeout.duration)
                case false => val authdb = connection(credentials.db) ; authdb.authenticate(credentials.user, credentials.password)(timeout.duration)
            }
            auth.map { _ => db(settings.collection) }
        })
    }
    
    def deleteCollection( genproc: Any, settings: MongoSettings ) =
    {
        collections.remove( (genproc, settings) ) 
        closeConnection(genproc)
    }
}