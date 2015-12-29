package tuktu.nosql.util

import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext.Implicits.global

import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.MongoConnection
import reactivemongo.api.MongoDriver
import reactivemongo.api.MongoConnectionOptions
import reactivemongo.api.AuthenticationMode
import reactivemongo.api.ScramSha1Authentication
import reactivemongo.api.CrAuthentication
import reactivemongo.core.nodeset.Authenticate

case class MongoSettings(hosts: List[String], database: String, collection: String)  

object MongoCollectionPool {
    import scala.concurrent.ExecutionContext.Implicits.global
    val driver = new MongoDriver
    val connections = scala.collection.mutable.HashMap[List[String],MongoConnection]()
    val map = scala.collection.mutable.HashMap[List[String],HashMap[String,HashMap[String, JSONCollection]]]()
    
    //create or get a collection
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
    def getCollectionWithCredentials(settings: MongoSettings, credentials: Authenticate, scramsha1: Boolean): JSONCollection = {
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
           Thread.sleep(10000L)
           val db = connection(settings.database)
           db(settings.collection)
        })        
    }
    
    // close connection is received, try to close all relevant connections
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