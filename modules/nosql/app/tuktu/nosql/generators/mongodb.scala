package tuktu.nosql.generators

import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import reactivemongo.api.MongoDriver
import tuktu.api._
import play.modules.reactivemongo.json.collection._

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

            // Get query and filter
            val query = (config \ "query").as[JsObject]
            val filter = (config \ "filter").as[JsObject]

            collection.
                find(query, filter).
                cursor[JsObject].
                enumerate().apply(Iteratee.foreach { doc =>
                    // Pipe the document into channel
                    channel.push(new DataPacket(List(
                            Map(resultName -> doc)
                    )))
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