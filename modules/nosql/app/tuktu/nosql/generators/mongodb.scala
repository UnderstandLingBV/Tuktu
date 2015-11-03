package tuktu.nosql.generators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.json.collection.JSONCollectionProducer
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
            val query = Json.parse((config \ "query").as[String])
            val filter = Json.parse((config \ "filter").asOpt[String].getOrElse("{}"))
            val sort = Json.parse((config \ "sort").asOpt[String].getOrElse("{}")).asInstanceOf[JsObject]
            val limit = (config \ "limit").asOpt[Int]
            val batchSize = (config \ "batch_size").asOpt[Int].getOrElse(50)
            
            // Batch all the results before pushing it on the channel
            val batch = (config \ "batch").asOpt[Boolean].getOrElse(false)
            
            val resultFuture = {
                // Get data based on query and filter
                val resultData = limit match {
                    case Some(s) => collection.find(query, filter)
                        .sort(sort).options(QueryOpts().batchSize(s))
                        .cursor[JsObject]
                        .collect[List](s)
                    case None => collection.find(query, filter).sort(sort)
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
                    e.printStackTrace
                    self ! new StopPacket
                }
            }
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}