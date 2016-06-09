package tuktu.nosql.processors.mongodb

import collection.immutable.SortedSet
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import tuktu.api.{ BaseProcessor, DataPacket }
import tuktu.api.utils.{ MapToJsObject, evaluateTuktuString }
import reactivemongo.core.nodeset.Authenticate
import play.modules.reactivemongo.json._
import scala.concurrent.Future
import scala.util.{ Failure, Success }
import reactivemongo.api.commands.WriteResult
import tuktu.nosql.util._

/**
 * Inserts data into MongoDB
 */

class MongoDBInsertProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fields = List[String]()
    var hosts: SortedSet[String] = _
    var database: String = _
    var coll: String = _
    var settings: MongoDBSettings = _
    var timeout: Int = _
    var user: Option[String] = _
    var pwd: String = _
    var admin: Boolean = _
    var scramsha1: Boolean = _
    var conn: Int = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hs = (config \ "hosts").as[List[String]] 
        hosts = SortedSet( hs: _* )
        database = (config \ "database").as[String]
        coll = (config \ "collection").as[String]
        conn = (config \ "connections").asOpt[Int].getOrElse(10)

        // Get credentials
        user = (config \ "user").asOpt[String]
        pwd = (config \ "password").asOpt[String].getOrElse("")
        admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

        // What fields to write?
        fields = (config \ "fields").as[List[String]]

        timeout = (config \ "timeout").asOpt[Int].getOrElse(Cache.getAs[Int]("timeout").getOrElse(30))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        val result = scala.collection.mutable.Map[(SortedSet[String], String, String), scala.collection.mutable.ListBuffer[JsObject]]()

        // Convert to JSON
        for (datum <- data.data) {
            result.getOrElseUpdate((hosts.map(evaluateTuktuString(_, datum)), evaluateTuktuString(database, datum), evaluateTuktuString(coll, datum)), scala.collection.mutable.ListBuffer[JsObject]()) += {
                fields match {
                    case Nil => MapToJsObject(datum, true)
                    case _   => MapToJsObject(datum.filter(elem => fields.contains(elem._1)), true)
                }
            }
        }

        // Bulk insert and await
        val futures = for (f <- result) yield {
            settings = MongoDBSettings(f._1._1, f._1._2, f._1._3)
            val fcollection = user match {
                case None => mongoTools.getFutureCollection(settings, conn)
                case Some(usr) => {
                    val credentials = admin match {
                        case true  => Authenticate("admin", usr, pwd)
                        case false => Authenticate(database, usr, pwd)
                    }
                    mongoTools.getFutureCollection(settings, credentials, scramsha1, conn)
                }
            }
            fcollection.flatMap{ collection => collection.bulkInsert(f._2.toStream, false) } 
        }
        // Wait for all the results to be retrieved
        Await.ready(Future.sequence(futures), timeout seconds)

        data
    })
}

/**
 * Insert a map or a JSON object contained in a field into MongoDB.
 */
class MongoDBFieldInsertProcessor(resultName: String) extends BaseProcessor(resultName) {
    var field: String = _
    var hosts: SortedSet[String] = _
    var database: String = _
    var coll: String = _
    var timeout: Int = _
    var user: Option[String] = _
    var pwd: String = _
    var admin: Boolean = _
    var scramsha1: Boolean = _
    var conn: Int = _
    
    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hs = (config \ "hosts").as[List[String]]
        hosts = SortedSet(hs: _*)  
        database = (config \ "database").as[String]
        coll = (config \ "collection").as[String]
        conn = (config \ "connections").asOpt[Int].getOrElse(10)
        
        // Get credentials
        user = (config \ "user").asOpt[String]
        pwd = (config \ "password").asOpt[String].getOrElse("")
        admin = (config \ "admin").as[Boolean]
        scramsha1 = (config \ "ScramSha1").as[Boolean]

        // What fields to write?
        field = (config \ "field").as[String]

        timeout = (config \ "timeout").asOpt[Int].getOrElse(Cache.getAs[Int]("timeout").getOrElse(30))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        doInsert(data)
        Future(data)
    })
    
    // Does the actual inserting
    def doInsert(data: DataPacket) = {
        // Insert field content into MongoDB
        Future.sequence(for (datum <- data.data; if (datum.contains(field)) ) yield
        {
            val jobj: JsObject = datum(field) match {
                case jobj: JsObject         => jobj
                case jmap: Map[String, Any] => tuktu.api.utils.MapToJsObject(jmap, false)
            }
            val settings = MongoDBSettings( (hosts.map(evaluateTuktuString(_, datum))) , evaluateTuktuString(database, datum), evaluateTuktuString(coll, datum))
            val fcollection = user match {
                case None => mongoTools.getFutureCollection( settings, conn )
                case Some(usr) => {
                    val credentials = admin match {
                        case true  => Authenticate("admin", usr, pwd)
                        case false => Authenticate(database, usr, pwd)
                    }
                    mongoTools.getFutureCollection(settings, credentials, scramsha1, conn)
                }
            }
            fcollection.flatMap{collection => collection.insert(jobj)} 
        })
    }
}