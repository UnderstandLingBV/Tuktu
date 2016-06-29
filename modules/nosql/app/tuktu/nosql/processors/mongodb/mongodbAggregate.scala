package tuktu.nosql.processors.mongodb

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import reactivemongo.api._
import reactivemongo.api.commands.AggregationFramework
import reactivemongo.core.nodeset.Authenticate
import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.nosql.util._
import scala.concurrent.Await
import play.api.cache.Cache
import akka.util.Timeout
import play.api.Play.current
import scala.concurrent.duration.DurationInt

/**
 * Aggregate data using the MongoDB aggregation pipeline
 */
class MongoDBAggregateProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var conn: MongoConnection = _
    var nodes: List[String] = _
    
    var db: String = _
    var collection: String = _
    var tasks: List[JsObject] = _

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
        
        // Get the connection
        val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
        conn = Await.result(fConnection, timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val fCollection = MongoPool.getCollection(conn, db, collection)
        fCollection.flatMap {coll =>
            // Prepare aggregation pipeline
            import coll.BatchCommands.AggregationFramework.PipelineOperator
            val transformer = new MongoPipelineTransformer()(coll)
            val pipeline = tasks.map { x => transformer.json2task(x)(collection=coll) }

            // Get data from Mongo
            val resultData = coll.aggregate(pipeline.head, pipeline.tail).map(_.result[JsObject])
            resultData.map { resultList => new DataPacket(for (resultRow <- resultList) yield { tuktu.api.utils.JsObjectToMap(resultRow) }) }
        }
    }) compose Enumeratee.onEOF(() => MongoPool.releaseConnection(nodes, conn))
}