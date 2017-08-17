package tuktu.nosql.processors.mongodb

import akka.util.Timeout
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.{ Json, JsObject, JsValue }
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import reactivemongo.api._
import reactivemongo.api.commands.AggregationFramework
import reactivemongo.core.nodeset.Authenticate
import scala.collection.immutable.SortedSet
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api._
import tuktu.nosql.util.{ MongoPool, MongoPipelineTransformer }

/**
 * Aggregate data using the MongoDB aggregation pipeline
 */
class MongoDBAggregateProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var conn: MongoConnection = _
    var nodes: List[String] = _

    var db: String = _
    var collection: String = _
    var tasks: List[JsValue] = _

    override def initialize(config: JsObject) {
        // Get hosts
        nodes = (config \ "hosts").as[List[String]]
        // Get connection properties
        val opts = (config \ "mongo_options").asOpt[JsObject]
        val mongoOptions = MongoPool.parseMongoOptions(opts)
        // Get credentials
        val auth = (config \ "auth").asOpt[JsObject].map { a =>
            Authenticate(
                (a \ "db").as[String],
                (a \ "user").as[String],
                (a \ "password").as[String])
        }

        // DB and collection
        db = (config \ "db").as[String]
        collection = (config \ "collection").as[String]

        // Get aggregation tasks
        tasks = (config \ "tasks").as[List[JsValue]]

        // Get the connection
        val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
        conn = Await.result(fConnection, timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val fCollection = MongoPool.getCollection(conn, db, collection)
        val newData = Future.sequence(for (datum <- data.data) yield {
            fCollection.flatMap { coll =>
                // Prepare aggregation pipeline
                val pipeline = tasks.map { task =>
                    MongoPipelineTransformer.json2task(utils.evaluateTuktuJsValue(task, datum).as[JsObject])(coll)
                }

                // Get data from Mongo
                val resultData: Future[List[JsObject]] = coll.aggregate(pipeline.head, pipeline.tail).map(_.head[JsObject])
                resultData.map { resultList =>
                    datum + (resultName -> resultList)
                }
            }
        })

        newData.map(nd => DataPacket(nd))
    }) compose Enumeratee.onEOF(() => MongoPool.releaseConnection(nodes, conn))
}