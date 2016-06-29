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
import scala.collection.immutable.SortedSet
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.nosql.util._

/**
 * Queries MongoDB for data
 */
// TODO: Support dynamic querying, is now static
class MongoDBFindProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var conn: MongoConnection = _
    var nodes: List[String] = _
    
    var db: String = _
    var collection: String = _
    var tasks: List[JsObject] = _
    
    var query: String = _
    var filter: String = _
    var sort: String = _
    var limit: Option[Int] = _
    
    override def initialize(config: JsObject) {
        // Get hosts
        nodes = (config \ "hosts").as[List[String]]
        // Get connection properties
        val opts = (config \ "mongo_options").asOpt[JsObject]
        val mongoOptions = MongoPool.parseMongoOptions(opts)
        // Get credentials
        val authentication = (config \ "auth").asOpt[JsObject]
        val auth = authentication match {
            case None => None
            case Some(a) => Some(Authenticate(
                    (a \ "db").as[String],
                    (a \ "user").as[String],
                    (a \ "password").as[String]
            ))
        }
        
        // DB and collection
        db = (config \ "db").as[String]
        collection = (config \ "collection").as[String]
        
        // Get aggregation tasks
        tasks = (config \ "tasks").as[List[JsObject]]
        
        // Get query and filter
        query = (config \ "query").as[String]
        filter = (config \ "filter").asOpt[String].getOrElse("{}")
        sort = (config \ "sort").asOpt[String].getOrElse("{}")
        limit = (config \ "limit").asOpt[Int]
        
        // Get the connection
        val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
        conn = Await.result(fConnection, timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Get collection
        val fCollection = MongoPool.getCollection(conn, db, collection)
        val results = fCollection.flatMap {collection =>
            Future.sequence(for (datum <- data.data) yield {
                // Evaluate the query and filter strings and convert to JSON
                val queryJson = Json.parse(stringHandler.evaluateString(query, datum, "\"", "")).as[JsObject]
                val filterJson = Json.parse(utils.evaluateTuktuString(filter, datum)).as[JsObject]
                val sortJson = Json.parse(utils.evaluateTuktuString(sort, datum)).asInstanceOf[JsObject]
                
                // Get data based on query and filter
                val resultData = limit match {
                    case Some(lmt) => collection.find(queryJson, filterJson)
                        .sort(sortJson).options(QueryOpts().batchSize(lmt))
                        .cursor[JsObject]().collect[List](lmt)
                    case None    => collection.find(queryJson, filterJson)
                        .sort(sortJson).cursor[JsObject]().collect[List]()
                }
                
                // Get the results in
                resultData.map(resultList => for (resultRow <- resultList) yield datum + (resultName -> tuktu.api.utils.JsObjectToMap(resultRow)))
            })
        }
        
        results.map(datums => new DataPacket(datums.flatten))
    })
}

/**
 * Queries MongoDB for data and streams the resulting records.
 */
class MongoDBFindStreamProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName)
{
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    // Set up the packet sender actor
    val packetSenderActor = Akka.system.actorOf(Props(classOf[PacketSenderActor], genActor))
    
    var settings, setts: MongoDBSettings = _
    var credentials: Option[Authenticate] = _
    var scramsha1: Boolean = _
    var query: String = _
    var filter: String = _
    var sort: String = _
    var readPreference: ReadPreference = _
    var keepjson: Boolean = _
    var conn: Int = _
    
    override def initialize(config: JsObject) 
    {
        // Set up MongoDB client
        val hs = (config \ "hosts").as[List[String]]
        val hosts = SortedSet(hs: _*)
        val db = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]
        settings = MongoDBSettings(hosts, db, coll)
        conn = (config \ "connections").asOpt[Int].getOrElse(10)
        
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

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        Future {
            doFind(data)
            data
        }
    })
    
    // Does the actual querying
    def doFind(data: DataPacket) = {
        // Update data into MongoDB
        Future.sequence(data.data.map(datum => {
            // evaluate credentials if needed
              val authenticate: Option[Authenticate] = credentials match{
                  case None => None
                  case Some(cred) => Option( Authenticate(utils.evaluateTuktuString(cred.db, datum), utils.evaluateTuktuString(cred.user, datum), utils.evaluateTuktuString(cred.password, datum) ) )
              }
              
              // evaluate settings
              setts = MongoDBSettings( settings.hosts.map { host => utils.evaluateTuktuString(host, datum) }, utils.evaluateTuktuString(settings.database, datum), utils.evaluateTuktuString(settings.collection, datum) )
              
              // get collection
              val fcollection = authenticate match{
                  case None => mongoTools.getFutureCollection(setts, conn)
                  case Some( auth ) => mongoTools.getFutureCollection(setts, auth, scramsha1, conn)
              }
              // Evaluate the query and filter strings and convert to JSON
              val queryJson = Json.parse(stringHandler.evaluateString(query, datum, "\"", "")).as[JsObject]
              val filterJson = Json.parse(utils.evaluateTuktuString(filter, datum)).as[JsObject]
              val sortJson = Json.parse(utils.evaluateTuktuString(sort, datum)).as[JsObject]
              
              // query database
              fcollection.flatMap{ collection =>
                val enumerator: Enumerator[JsObject] = collection.find(queryJson, filterJson).sort(sortJson).cursor[JsObject](readPreference).enumerate()
                val pushRecords: Iteratee[JsObject, Unit] = Iteratee.foreach { record => keepjson match{
                  case true => (packetSenderActor ! (datum + ( resultName -> record )))
                  case false => (packetSenderActor ! (datum + ( resultName -> tuktu.api.utils.JsObjectToMap(record) )))
                }}
                enumerator.run(pushRecords)
              }
        }))
    }
    
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