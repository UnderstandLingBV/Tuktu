package tuktu.nosql.processors.mongodb

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee

import play.api.libs.json._
import play.api.libs.json.Json._

import play.api.Play.current

import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._

import reactivemongo.api.commands.Command
import reactivemongo.api.DefaultDB
import reactivemongo.api.MongoDriver
import reactivemongo.api._
import reactivemongo.api.commands.AggregationFramework

import scala.collection.Set

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

import tuktu.api._
import tuktu.nosql.util._

/**
 * Queries MongoDB for data
 */
// TODO: Support dynamic querying, is now static
class MongoDBAggregateProcessor(resultName: String) extends BaseProcessor(resultName) 
{
    var settings: MongoSettings = _
    var tasks: List[JsObject] = _
    // var explain: Boolean = _
    // var allowDiskUse: Boolean = _
    // var cursor: Option[Cursor] = _

    override def initialize(config: JsObject) 
    {
        // Set up MongoDB client
        val hosts = (config \ "hosts").as[List[String]]
        val database = (config \ "database").as[String]
        val coll = (config \ "collection").as[String]

        // Prepare connection settings
        settings = MongoSettings(hosts, database, coll)

        // Get aggregation tasks
        tasks = (config \ "tasks").as[List[JsObject]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM( (data: DataPacket) => {
        implicit val collection: JSONCollection = MongoCollectionPool.getCollection( settings )
        import collection.BatchCommands.AggregationFramework.PipelineOperator
        // prepare aggregation pipeline
        val transformer: MongoPipelineTransformer = new MongoPipelineTransformer()( collection )
        val pipeline: List[PipelineOperator] = tasks.map { x => transformer.json2task( x ) }
      
        // get data from Mongo
        import collection.BatchCommands.AggregationFramework.AggregationResult
        val resultData: Future[List[JsObject]] = collection.aggregate( pipeline.head, pipeline.tail ).map(_.result[JsObject])

        resultData.map{ resultList => new DataPacket( for (resultRow <- resultList) yield{ tuktu.api.utils.JsObjectToMap(resultRow) } ) }
    })
     
}