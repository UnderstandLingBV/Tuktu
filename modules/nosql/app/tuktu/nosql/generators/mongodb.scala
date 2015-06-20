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

            // Get query
            val query = (config \ "query")     
            
            // Batch all the results before pushing it on the channel
            val batch = (config \ "batch").asOpt[Boolean].getOrElse(false)

            if (batch) {
                val fut = collection.find(query).cursor[JsObject].collect[List]().map {
                    list =>
                        list.map {
                            obj => tuktu.api.utils.JsObjectToMap(obj)
                        }
                }
                
                // Determine what to do and clean up connection
                fut onSuccess {
                    case list: List[Map[String, Any]] => {
                        channel.push(new DataPacket(list))
                        self ! new StopPacket
                    }
                }
                fut onFailure {
                    case e: Throwable => {
                        e.printStackTrace
                        self ! new StopPacket
                    }
                }
            } else {
                val fut = collection.
                    find(query).
                    cursor[JsObject].
                    enumerate().apply({
                        Iteratee.foreach { doc =>
                        // Pipe the document into channel
                        channel.push(new DataPacket(List(
                            tuktu.api.utils.JsObjectToMap(doc))))
                        }
                    })
                                    
                fut onComplete {
                    case _ => {
                        self ! new StopPacket
                    }
                }
            }
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}