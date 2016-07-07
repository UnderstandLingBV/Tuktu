package tuktu.nosql.processors.mongodb

import akka.util.Timeout
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.Play.current
import play.modules.reactivemongo.json._
import reactivemongo.api._
import reactivemongo.api.commands.Command
import reactivemongo.core.commands.SuccessfulAuthentication
import reactivemongo.core.nodeset.Authenticate
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.api.utils.{ MapToJsObject, evaluateTuktuString }
import tuktu.nosql.util.MongoPool
import scala.concurrent.Await

/**
 * Provides a helper to run specified database commands (as long as the command result is less than 16MB in size).
 */
class MongoDBRawCommandProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    var conn: MongoConnection = _
    var nodes: List[String] = _
    
    var db: String = _
    
    var command: String = _
    var resultOnly: Boolean = _

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
        
        // Get command
        command = (config \ "command").as[String]
        // Get result format
        resultOnly = (config \ "resultOnly").asOpt[Boolean].getOrElse(false)
        
        // Get the connection
        val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
        conn = Await.result(fConnection, timeout.duration)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val futs = Future.sequence(for (datum <- data.data) yield {
            val dbEval = utils.evaluateTuktuString(db, datum)
            
            // Get collection
            val fDb = conn.database(dbEval)
            fDb.flatMap(d => {
                // Set up the runner
                val runner = Command.run(JSONSerializationPack)
                // Get the command
                val jcommand = Json.parse(evaluateTuktuString(command, datum)).as[JsObject]
                val result = runner(d, runner.rawCommand(jcommand)).one[JsObject]
                    
                result.map {r =>
                    if (resultOnly) datum + (resultName -> (r \ "result"))
                    else datum + (resultName -> r)
                }
            })
        })
        futs.map(result => new DataPacket(result))
    }) compose Enumeratee.onEOF(() => MongoPool.releaseConnection(nodes, conn))

}