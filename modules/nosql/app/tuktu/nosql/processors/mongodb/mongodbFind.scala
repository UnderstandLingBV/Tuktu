package tuktu.nosql.processors.mongodb

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee._
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.Play.current
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import reactivemongo.api._
import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.nosql.util.MongoCollectionPool
import tuktu.nosql.util.MongoSettings
import tuktu.nosql.util.sql
import tuktu.nosql.util.stringHandler

/**
 * Queries MongoDB for data
 */
// TODO: Support dynamic querying, is now static
class MongoDBFindProcessor(resultName: String) extends BaseProcessor(resultName) {
    var collection: JSONCollection = _
    var query: String = _
    var filter: String = _
    var sort: String = _
    var limit: Option[Int] = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]
        
        // Get credentials
        val user = (config \ "user").asOpt[String]
        val pwd = (config \ "password").asOpt[String].getOrElse("")
        val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        val scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

        // Set up connection
        val settings = MongoSettings(hosts, database, coll)
        collection = user match{
            case None => MongoCollectionPool.getCollection(settings)
            case Some( usr ) => {
                val credentials = admin match
                {
                  case true => Authenticate( "admin", usr, pwd )
                  case false => Authenticate( database, usr, pwd )
                }
                MongoCollectionPool.getCollectionWithCredentials(settings,credentials, scramsha1)
              }
          }
        
        // Get query and filter
        query = (config \ "query").as[String]
        filter = (config \ "filter").asOpt[String].getOrElse("{}")
        sort = (config \ "sort").asOpt[String].getOrElse("{}")
        limit = (config \ "limit").asOpt[Int]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Get data from MongoDB and sequence
        val resultListFutures = Future.sequence(for (datum <- data.data) yield {
            // Evaluate the query and filter strings and convert to JSON
            val queryJson = Json.parse(stringHandler.evaluateString(query, datum, "\"", "")).as[JsObject]
            val filterJson = Json.parse(utils.evaluateTuktuString(filter, datum)).as[JsObject]
            val sortJson = Json.parse(utils.evaluateTuktuString(sort, datum)).asInstanceOf[JsObject]

            // Get data based on query and filter
            val resultData: Future[List[JsObject]] = limit match {
                case Some(s) => collection.find(queryJson, filterJson)
                    .sort(sortJson).options(QueryOpts().batchSize(s)).cursor[JsObject].collect[List](s)
                case None    => collection.find(queryJson, filterJson)
                    .sort(sortJson).cursor[JsObject].collect[List]()
            }

            resultData.map(resultList => {
                for (resultRow <- resultList) yield {
                    tuktu.api.utils.JsObjectToMap(resultRow)
                }
            })
        })

        // Iterate over result futures
        val dataFuture = resultListFutures.map(resultList => {
            // Combine result with data
            for ((result, datum) <- resultList.zip(data.data)) yield {
                datum + (resultName -> result)
            }
        })

        // Gather results
        new DataPacket(Await.result(dataFuture, Cache.getAs[Int]("timeout").getOrElse(30) seconds))
    })
}

/**
 * Queries MongoDB for data
 */
class MongoDBFindStreamProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName)
{
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    // Set up the packet sender actor
    val packetSenderActor = Akka.system.actorOf(Props(classOf[PacketSenderActor], genActor))
    
    var settings: MongoSettings = _
    var credentials: Option[Authenticate] = _
    var scramsha1: Boolean = _
    var query: String = _
    var filter: String = _
    var sort: String = _
    var readPreference: ReadPreference = _
    var keepjson: Boolean = _

    override def initialize(config: JsObject) {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val db = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]
        settings = MongoSettings(hosts, db, coll)
        
        // Get credentials
        val user = (config \ "user").asOpt[String]
        val pwd = (config \ "password").asOpt[String].getOrElse("")
        val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        
        scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

        // Set up connection
        credentials = user match{
            case None => None
            case Some( usr ) => {
                admin match
                {
                  case true => Option(Authenticate( "admin", usr, pwd ))
                  case false => Option(Authenticate( db, usr, pwd ))
                }
            }
        }
        
        
        // Get query and filter
        query = (config \ "query").as[String]
        filter = (config \ "filter").asOpt[String].getOrElse("{}")
        sort = (config \ "sort").asOpt[String].getOrElse("{}")
        
        // Get read preference and keepJson
        keepjson = (config \ "keepAsJson").asOpt[Boolean].getOrElse(true)
        val readPref = (config \ "readPreference").asOpt[String].getOrElse("nearest")
        readPreference = readPref match{
          case "nearest" => ReadPreference.nearest
          case "primary" => ReadPreference.primary
          case "primaryPreferred" => ReadPreference.primaryPreferred
          case "secondary" => ReadPreference.secondary
          case "secondaryPreferred" => ReadPreference.secondaryPreferred
          case _ => ReadPreference.nearest
        }
        
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
      val futures = Future.sequence(  
          for( datum <- data.data ) yield
          { 
              // evaluate credentials if needed
              val authenticate: Option[Authenticate] = credentials match{
                  case None => None
                  case Some(cred) => Option( Authenticate(utils.evaluateTuktuString(cred.db, datum), utils.evaluateTuktuString(cred.user, datum), utils.evaluateTuktuString(cred.password, datum) ) )
              }
              
              // evaluate settings
              val setts = MongoSettings( settings.hosts, utils.evaluateTuktuString(settings.database, datum), utils.evaluateTuktuString(settings.collection, datum) )
              
              // get collection
              val collection = authenticate match{
                  case None => MongoCollectionPool.getCollection(setts)
                  case Some( auth ) => MongoCollectionPool.getCollectionWithCredentials(setts, auth, scramsha1)
              }
              // Evaluate the query and filter strings and convert to JSON
              val queryJson = Json.parse(stringHandler.evaluateString(query, datum, "\"", "")).as[JsObject]
              val filterJson = Json.parse(utils.evaluateTuktuString(filter, datum)).as[JsObject]
              val sortJson = Json.parse(utils.evaluateTuktuString(sort, datum)).as[JsObject]
              
              // query database
              val enumerator: Enumerator[JsObject] = collection.find(queryJson, filterJson).sort(sortJson).cursor[JsObject](readPreference).enumerate()
              val pushRecords: Iteratee[JsObject, Unit] = Iteratee.foreach { record => keepjson match{
                  case true => (packetSenderActor ! (datum + ( resultName -> record )))
                  case false => (packetSenderActor ! (datum + ( resultName -> tuktu.api.utils.JsObjectToMap(record) )))
              } }
              enumerator.run(pushRecords)
          })
      data
    })
}

/**
 * Actor for forwarding data packets
 */
class PacketSenderActor(remoteGenerator: ActorRef) extends Actor with ActorLogging {
    remoteGenerator ! new InitPacket
    
    def receive() = {
        case sp: StopPacket => {
            remoteGenerator ! sp
            self ! PoisonPill
            println( "stopped" )
        }
        case datum: Map[String, Any] => {
            // Directly forward
            remoteGenerator ! new DataPacket(List(datum))
            sender ! "ok"
        }
    }
}