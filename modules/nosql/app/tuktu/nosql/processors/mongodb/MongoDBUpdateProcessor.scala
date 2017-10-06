package tuktu.nosql.processors.mongodb

import akka.util.Timeout
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.{ JsObject, JsValue }
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.MongoConnection
import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.{ BaseProcessor, DataPacket }
import tuktu.api.utils
import tuktu.nosql.util.MongoPool

/**
 * Updates data into MongoDB
 */
class MongoDBUpdateProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var conn: MongoConnection = _
    var nodes: List[String] = _

    var db: utils.evaluateTuktuString.TuktuStringRoot = _
    var collection: utils.evaluateTuktuString.TuktuStringRoot = _

    var waitForCompletion: Boolean = _

    // If set to true, creates a new document when no document matches the query criteria. 
    var upsert: Boolean = _
    // The selection criteria for the update. 
    var query: utils.PreparedJsNode = _
    // The modifications to apply. 
    var update: utils.PreparedJsNode = _
    //  If set to true, updates multiple documents that meet the query criteria. If set to false, updates one document. 
    var multi: Boolean = _

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
        db = utils.evaluateTuktuString.prepare((config \ "db").as[String])
        collection = utils.evaluateTuktuString.prepare((config \ "collection").as[String])

        // Query stuff
        query = utils.prepareTuktuJsValue(config \ "query")
        update = utils.prepareTuktuJsValue(config \ "update")
        upsert = (config \ "upsert").asOpt[Boolean].getOrElse(false)
        multi = (config \ "multi").asOpt[Boolean].getOrElse(false)

        // Wait for updates to complete?
        waitForCompletion = (config \ "wait_for_completion").asOpt[Boolean].getOrElse(false)

        // Get the connection
        val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
        conn = Await.result(fConnection, timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val jsons = (for (datum <- data.data) yield {
            (
                db.evaluate(datum),
                collection.evaluate(datum),
                (
                    query.evaluate(datum).as[JsObject],
                    update.evaluate(datum).as[JsObject]))
        }).groupBy { case (db, coll, _) => (db, coll) }

        // Execute per DB/Collection pair
        val resultFut = Future.sequence(for {
            ((dbEval, collEval), queries) <- jsons
        } yield {
            MongoPool.getCollection(conn, dbEval, collEval).flatMap { collection =>
                import collection.BatchCommands._
                import UpdateCommand._

                val elements = for ((_, _, (selector, updater)) <- queries) yield {
                    UpdateElement(q = selector, u = updater, upsert = upsert, multi = multi)
                }

                collection.runCommand(Update(elements.head, elements.tail: _*))
            }
        })

        // Continue directly or wait?
        if (waitForCompletion) resultFut.map { _ => data }
        else Future { data }
    }) compose Enumeratee.onEOF(() => MongoPool.releaseConnection(nodes, conn))
}