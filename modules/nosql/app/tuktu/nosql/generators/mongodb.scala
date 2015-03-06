package tuktu.nosql.generators

import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import reactivemongo.api.MongoDriver
import reactivemongo.bson.BSONDocument
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONString
import reactivemongo.bson.BSONValue.ExtendedBSONValue
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.StopPacket

class MongoDBGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    def MapReader[V](implicit vr: BSONDocumentReader[V]): BSONDocumentReader[Map[String, V]] = new BSONDocumentReader[Map[String, V]] {
        def read(bson: BSONDocument): Map[String, V] = {
            val elements = bson.elements.map { tuple =>
                // Assume that all values in the document are BSONDocuments
                tuple._1 -> vr.read(tuple._2.seeAsTry[BSONDocument].get)
            }
            elements.toMap
        }
    }

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
            val collection = db(coll)

            // Set up query for collection
            val queryJson = (config \ "query").as[JsObject]
            val query = BSONDocument((for (key <- queryJson.keys) yield {
                key -> BSONString((queryJson \ key).as[String])
            }).toMap)
            
            // Set up filter
            val filterJson = (config \ "filter").as[JsObject]
            val filter = BSONDocument((for (key <- filterJson.keys) yield {
                key -> BSONString((filterJson \ key).as[String])
            }).toMap)

            collection.
                find(query, filter).
                cursor[BSONDocument].
                enumerate().apply(Iteratee.foreach { doc =>
                    // Pipe the document into channel
                    channel.push(new DataPacket(List(MapReader.read(doc))))
                })
                
            connection.close()
            driver.close()
            self ! StopPacket
        }
        case sp: StopPacket => {
            cleanup()
        }
    }
}