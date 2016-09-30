package tuktu.nosql.processors.mongodb

import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.modules.reactivemongo.json._
import reactivemongo.core.nodeset.Authenticate
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.MongoConnection
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }
import tuktu.api.{ BaseProcessor, DataPacket }
import tuktu.api.utils.{ MapToJsObject, evaluateTuktuString }
import tuktu.nosql.util.MongoPool

/**
 * Inserts data into MongoDB
 */

class MongoDBInsertProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var conn: MongoConnection = _
    var nodes: List[String] = _

    var db: String = _
    var collection: String = _

    var fields: List[String] = _

    var waitForCompletion: Boolean = _

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

        // What fields to write?
        fields = (config \ "fields").as[List[String]]

        // Wait for updates to complete?
        waitForCompletion = (config \ "wait_for_completion").asOpt[Boolean].getOrElse(false)

        // Get the connection
        val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
        conn = Await.result(fConnection, timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val jsons = (for (datum <- data.data) yield {
            (
                evaluateTuktuString(db, datum),
                evaluateTuktuString(collection, datum),
                fields match { // Convert to JSON
                    case Nil => MapToJsObject(datum, true)
                    case _   => MapToJsObject(datum.filterKeys(key => fields.contains(key)), true)
                })
        }).toList.groupBy(_._1).map(elem => elem._1 -> elem._2.groupBy(_._2))

        // Execute per DB/Collection pair
        val resultFut = Future.sequence(for {
            (dbEval, collectionMap) <- jsons
            (collEval, queries) <- collectionMap
        } yield {
            val fCollection = MongoPool.getCollection(conn, dbEval, collEval)
            fCollection.flatMap(coll => coll.bulkInsert(queries.map(_._3).toStream, false))
        })

        // Continue directly or wait?
        if (waitForCompletion) resultFut.map { _ => data }
        else Future { data }
    }) compose Enumeratee.onEOF(() => MongoPool.releaseConnection(nodes, conn))
}