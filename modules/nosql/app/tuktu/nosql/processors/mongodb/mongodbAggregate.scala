package tuktu.nosql.processors.mongodb

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import reactivemongo.api._
import reactivemongo.api.commands.AggregationFramework
import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.nosql.util._

/**
 * Queries MongoDB for data
 */
// TODO: Support dynamic querying, is now static
class MongoDBAggregateProcessor(resultName: String) extends BaseProcessor(resultName) {
    var settings: MongoSettings = _
    var tasks: List[JsObject] = _
    var user: Option[String] = _
    var pwd: String = _
    var admin: Boolean = _
    var scramsha1: Boolean = _

    override def initialize(config: JsObject) {
        // Get hosts, database and collection
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]

        // Get credentials
        user = (config \ "user").asOpt[String]
        pwd = (config \ "password").asOpt[String].getOrElse("")
        admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

        // Prepare connection settings
        settings = MongoSettings(hosts, database, coll)

        // Get aggregation tasks
        tasks = (config \ "tasks").as[List[JsObject]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        implicit val collection: JSONCollection = user match {
            case None => MongoCollectionPool.getCollection(settings)
            case Some(usr) => {
                val credentials = admin match {
                    case true  => Authenticate("admin", usr, pwd)
                    case false => Authenticate(settings.database, usr, pwd)
                }
                MongoCollectionPool.getCollectionWithCredentials(settings, credentials, scramsha1)
            }
        }

        import collection.BatchCommands.AggregationFramework.PipelineOperator
        // Prepare aggregation pipeline
        val transformer: MongoPipelineTransformer = new MongoPipelineTransformer()(collection)
        val pipeline: List[PipelineOperator] = tasks.map { x => transformer.json2task(x) }

        // Get data from Mongo
        val resultData: Future[List[JsObject]] = collection.aggregate(pipeline.head, pipeline.tail).map(_.result[JsObject])

        resultData.map { resultList => new DataPacket(for (resultRow <- resultList) yield { tuktu.api.utils.JsObjectToMap(resultRow) }) }
    })

}