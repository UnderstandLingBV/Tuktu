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

class MongoDBGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get connection properties
            val hosts = (config \ "hosts").as[List[String]]
            val database = (config \ "database").as[String]
            val coll = (config \ "collection").as[String]

            // Set up connection
            val driver = new MongoDriver
            val connection = driver.connection(hosts)
            // Connect to DB
            val db = connection(database)
            // Select the collection
            val collection: JSONCollection = db(coll)

            // Get query
            val query = (config \ "query")     
            
            // Batch all the results before pushing it on the channel
            val batch = (config \ "batch").asOpt[Boolean].getOrElse(false)

            if (batch) {
                val fut = collection.find(query).cursor[JsObject].collect[List]().map {
                    list =>
                        list.map {
                            obj => tuktu.api.utils.anyJsonToMap(obj)
                        }
                }
                
                // Determine what to do and clean up connection
                fut onSuccess {
                    case list: List[Map[String, Any]] => {
                        channel.push(new DataPacket(list))
                        connection.close
                        self ! new StopPacket
                    }
                }
                fut onFailure {
                    case e: Throwable => {
                        e.printStackTrace
                        connection.close
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
                            tuktu.api.utils.anyJsonToMap(doc))))
                        }
                    })
                    
                // Close connection
                fut onComplete {
                    case _ => {
                        connection.close
                        self ! new StopPacket
                    }
                }
            }
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}