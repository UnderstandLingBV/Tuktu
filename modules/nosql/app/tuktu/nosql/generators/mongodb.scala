package tuktu.nosql.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import reactivemongo.api.MongoDriver
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import scala.util.Failure
import tuktu.api.InitPacket
import tuktu.nosql.util.MongoCollectionPool
import tuktu.nosql.util.MongoSettings
import reactivemongo.api.QueryOpts
import play.api.libs.json.Json
import scala.concurrent.Future
import play.api.Logger
import tuktu.nosql.util.MongoPipelineTransformer

class MongoDBGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get connection properties
            val hosts = (config \ "hosts").as[List[String]]
            val database = (config \ "database").as[String]
            val coll = (config \ "collection").as[String]

            // Set up connection
            val settings = MongoSettings(hosts, database, coll)
            val collection = MongoCollectionPool.getCollection(settings)

            // Get query and filter
            val query = (config \ "query").as[JsObject]
            val filter = (config \ "filter").asOpt[JsObject].getOrElse( Json.obj() )
            val sort = (config \ "sort").asOpt[JsObject].getOrElse( Json.obj() )
            
            // Batch all the results before pushing it on the channel
            val batch = (config \ "batch").asOpt[Boolean].getOrElse(false)
            val limit = (config \ "limit").asOpt[Int]
            val batchSize = (config \ "batch_size").asOpt[Int].getOrElse(50)
            
            val resultFuture = {
                // Get data based on query and filter
                val resultData = limit match {
                    case Some(s) => collection.find(query,filter).sort(sort)
                        .options(QueryOpts().batchSize(s)).cursor[JsObject].collect[List](s)
                    case None => collection.find(query,filter).sort(sort)
                        .options(QueryOpts().batchSize(batchSize)).cursor[JsObject].collect[List]()
                }
                // Get futures into JSON
                resultData.map { resultList => {
                    for (resultRow <- resultList) yield {
                        tuktu.api.utils.JsObjectToMap(resultRow)
                    }
                }}  
            }

            // Handle results
            resultFuture.onSuccess {
                case res: List[Map[String, Any]] => {
                    // Determine what to do based on batch or non batch
                    if (batch)
                        channel.push(new DataPacket(res))
                    else
                        res.foreach(row => channel.push(new DataPacket(List(row))))
                        
                    self ! new StopPacket()
                }
                case _ => self ! new StopPacket()
            }
            resultFuture.onFailure {
                case e: Throwable => {
                    Logger.error("Error executing MongoDB Query",e)                    
                    self ! new StopPacket
                }
            }
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}

/**
 * Generator for MongoDB aggregations
 */
class MongoDBAggregateGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get connection properties
            val hosts = (config \ "hosts").as[List[String]]
            val database = (config \ "database").as[String]
            val coll = (config \ "collection").as[String]

            // Set up connection
            val settings = MongoSettings(hosts, database, coll)
            implicit val collection = MongoCollectionPool.getCollection(settings)

            // Get tasks
            val tasks = (config \ "tasks").as[List[JsObject]]
            
            // Batch all the results before pushing it on the channel
            val batch = (config \ "batch").asOpt[Boolean].getOrElse(false)
            
            // prepare aggregation pipeline
            import collection.BatchCommands.AggregationFramework.PipelineOperator
            val transformer: MongoPipelineTransformer = new MongoPipelineTransformer()( collection )
            val pipeline: List[PipelineOperator] = tasks.map { x => transformer.json2task( x ) }
      
            val resultFuture = {
                // Get data based on the aggregation pipeline
                import collection.BatchCommands.AggregationFramework.AggregationResult
                val resultData: Future[List[JsObject]] = collection.aggregate( pipeline.head, pipeline.tail ).map(_.result[JsObject])
                // Get futures into JSON
                resultData.map { resultList => {
                    for (resultRow <- resultList) yield {
                        tuktu.api.utils.JsObjectToMap(resultRow)
                    }
                }}  
            }

            // Handle results
            resultFuture.onSuccess {
                case res: List[Map[String, Any]] => {
                    // Determine what to do based on batch or non batch
                    if (batch)
                        channel.push(new DataPacket(res))
                    else
                        res.foreach(row => channel.push(new DataPacket(List(row))))
                }
                case _ => self ! new StopPacket()
            }
            resultFuture.onFailure {
                case e: Throwable => {
                    e.printStackTrace
                    self ! new StopPacket
                }
            }
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}