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

case class MongoSettings(hosts: List[String], database: String, collection: String)  

object MongoCollectionPool {
  
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
  
    import scala.concurrent.ExecutionContext.Implicits.global
    val driver = new MongoDriver
    val connections = scala.collection.mutable.HashMap[List[String],MongoConnection]()
    val map = scala.collection.mutable.HashMap[List[String],HashMap[String,HashMap[String, JSONCollection]]]()
    val fmap = scala.collection.mutable.HashMap[List[String],HashMap[String,HashMap[String, Future[JSONCollection]]]]()
    
    //create or get a collection
    @deprecated("Prefer MongoTools non-blocking method getFutureCollection","2016-02-05")
    def getCollection(settings: MongoSettings): JSONCollection = {
        map.getOrElseUpdate(settings.hosts, {
            HashMap[String,HashMap[String, JSONCollection]]()
        }).getOrElseUpdate(settings.database, {
            HashMap[String, JSONCollection]()
        }).getOrElseUpdate(settings.collection, {
           val connection = connections.getOrElseUpdate(settings.hosts, driver.connection(settings.hosts))
           val db = connection(settings.database)
           db(settings.collection)
        })        
    }
    
        //create or get a collection with credentials
    // http://reactivemongo.org/releases/0.11/documentation/tutorial/connect-database.html
    @deprecated("Prefer MongoTools non-blocking method getFutureCollection","2016-02-05")
    def getCollectionWithCredentials(settings: MongoSettings, credentials: Authenticate, scramsha1: Boolean): JSONCollection = 
    {
        map.getOrElseUpdate(settings.hosts, {
            HashMap[String,HashMap[String, JSONCollection]]()
        }).getOrElseUpdate(settings.database, {
            HashMap[String, JSONCollection]()
        }).getOrElseUpdate(settings.collection, {
           val conOpts = scramsha1 match{
             case true => MongoConnectionOptions( authMode = ScramSha1Authentication )
             case false => MongoConnectionOptions( authMode = CrAuthentication )
           }
           val connection = connections.getOrElseUpdate(settings.hosts, driver.connection(settings.hosts, options = conOpts, authentications = List(credentials)))
           // TODO find a better way to wait for authentication to succeed
           // should be solved by upgrading to reactivemongo 0.11.9 (which requires play 2.4) with which authentication returns a Future that can be handled explicitly
           Thread.sleep(10000L)
           val db = connection(settings.database)
           db(settings.collection)
           
        })        
    }
    @deprecated("Prefer MongoTools non-blocking method getFutureCollection","2016-02-05")
    def getFutureCollectionWithCredentials(settings: MongoSettings, credentials: Authenticate, scramsha1: Boolean): Future[JSONCollection] = 
    {
       fmap.getOrElseUpdate(settings.hosts, {
           HashMap[String,HashMap[String, Future[JSONCollection]]]()
       }).getOrElseUpdate(settings.database, {
           HashMap[String, Future[JSONCollection]]()
       }).getOrElseUpdate(settings.collection, {
          val conOpts = scramsha1 match{
            case true => MongoConnectionOptions( authMode = ScramSha1Authentication )
            case false => MongoConnectionOptions( authMode = CrAuthentication )
          }
          
          val connection = connections.getOrElseUpdate(settings.hosts, driver.connection(settings.hosts, options = conOpts))
          // Authenticate
          val db = connection(settings.database)
          val auth = (credentials.db.equals( settings.database )) match {
            case true => db.authenticate(credentials.user, credentials.password)(timeout.duration)
            case false => val authdb = connection(credentials.db) ; authdb.authenticate(credentials.user, credentials.password)(timeout.duration)
          }
          auth.map { _ => db(settings.collection)}
       })        
   }
    
    
    
    
    // close connection is received, try to close all relevant connections
    @deprecated("Prefer MongoTools method deleteCollection","2016-02-05")
    def closeCollection(settings: MongoSettings) {
        map.synchronized{
            map.get(settings.hosts) match {
                case a => a.get(settings.database) match {
                    case b => b.remove(settings.collection)                
                }            
            }
    
            map.get(settings.hosts) match {
                case a => if(a.get(settings.database).isEmpty) {    
                    a.get.remove(settings.database)
                    if (a.get.isEmpty) {
                        map.remove(settings.hosts)                    
                        connections.remove(settings.hosts).get.close
                    }
                }
            }
        }
    }
}