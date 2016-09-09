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
        
        // Get query and filter
        query = (config \ "query").as[JsObject].toString
        filter = (config \ "filter").asOpt[JsObject].getOrElse(Json.obj()).toString
        sort = (config \ "sort").asOpt[JsObject].getOrElse(Json.obj()).toString
        limit = (config \ "limit").asOpt[Int]
        
        // Get the connection
        val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
        conn = Await.result(fConnection, timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val results = Future.sequence(for (datum <- data.data) yield {
            val dbEval = utils.evaluateTuktuString(db, datum)
            val collEval = utils.evaluateTuktuString(collection, datum)
            
            // Get collection
            val fCollection = MongoPool.getCollection(conn, dbEval, collEval)
            fCollection.flatMap(coll => {
                // Evaluate the query and filter strings and convert to JSON
                val queryJson = Json.parse(utils.evaluateTuktuString(query.toString, datum)).as[JsObject]
                val filterJson = Json.parse(utils.evaluateTuktuString(filter, datum)).as[JsObject]
                val sortJson = Json.parse(utils.evaluateTuktuString(sort, datum)).asInstanceOf[JsObject]
                
                // Get data based on query and filter
                val resultData = limit match {
                    case Some(lmt) => coll.find(queryJson, filterJson)
                        .sort(sortJson).options(QueryOpts().batchSize(lmt))
                        .cursor[JsObject]().collect[List](lmt)
                    case None    => coll.find(queryJson, filterJson)
                        .sort(sortJson).cursor[JsObject]().collect[List]()
                }
                
                // Get the results in
                 resultData.map { resultList =>
                    if (resultList.isEmpty)
                        datum + (resultName -> List.empty[JsObject])
                    else {
                        datum + (resultName -> resultList)
                    }
                }
            })
        })
        
        results.map(nd => DataPacket(nd))
    }) compose Enumeratee.onEOF(() => MongoPool.releaseConnection(nodes, conn))
}

/**
 * Queries MongoDB for data and streams the resulting records.
 */
class MongoDBFindStreamProcessor(genActor: ActorRef, resultName: String) extends BufferProcessor(genActor, resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var conn: MongoConnection = _
    var nodes: List[String] = _
    
    var db: String = _
    var collection: String = _
    
    var query: String = _
    var filter: String = _
    var sort: String = _
    
    // Set up the packet sender actor
    val packetSenderActor = Akka.system.actorOf(Props(classOf[PacketSenderActor], genActor))
    
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
        
        // Get query and filter
        query = (config \ "query").as[JsObject].toString
        filter = (config \ "filter").asOpt[JsObject].getOrElse(Json.obj()).toString
        sort = (config \ "sort").asOpt[JsObject].getOrElse(Json.obj()).toString
        
        // Get the connection
        val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
        conn = Await.result(fConnection, timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) {
            val dbEval = utils.evaluateTuktuString(db, datum)
            val collEval = utils.evaluateTuktuString(collection, datum)
            
            // Get collection
            val fCollection = MongoPool.getCollection(conn, dbEval, collEval)
            fCollection.map {collection =>
                // Evaluate the query and filter strings and convert to JSON
                val queryJson = Json.parse(stringHandler.evaluateString(query, datum, "\"", "")).as[JsObject]
                val filterJson = Json.parse(utils.evaluateTuktuString(filter, datum)).as[JsObject]
                val sortJson = Json.parse(utils.evaluateTuktuString(sort, datum)).asInstanceOf[JsObject]
              
                // Query database and forward to our actor
                val enumerator: Enumerator[JsObject] = collection.find(queryJson, filterJson)
                    .sort(sortJson).cursor[JsObject]().enumerate().andThen(Enumerator.eof)

                // Chain the stuff together with proper forwarding and EOF handling
                enumerator |>> (
                        Enumeratee.mapM[JsObject](record => Future {
                            packetSenderActor ! (datum + (resultName -> tuktu.api.utils.JsObjectToMap(record)))
                            record
                        })
                ) &>> Iteratee.ignore
                //enumerator.run(pushRecords)
            }
        }
        
        data
    }) compose Enumeratee.onEOF(() => {
        packetSenderActor ! new StopPacket
        MongoPool.releaseConnection(nodes, conn)
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
        }
        case datum: Map[String, Any] => {
            // Directly forward
            remoteGenerator ! DataPacket(List(datum))
            sender ! "ok"
        }
    }
}