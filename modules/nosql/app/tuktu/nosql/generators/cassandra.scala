package tuktu.nosql.generators

import scala.collection.JavaConversions.iterableAsScalaIterable
import akka.actor.ActorRef
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import tuktu.nosql.util.cassandra
import tuktu.api.InitPacket

class CassandraGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get the hostname
            val host = (config \ "host").as[String]

            // Set up the connection
            val client = new cassandra.client(host)

            // Determine how this query should be executed
            val executionType = (config \ "type").asOpt[String].getOrElse("default")

            // Get the query
            val query = (config \ "query").as[String]
            // Do we need to flatten or not?
            val flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
            
            // Get the fetch size
            val fetchSize = (config \ "fetch_size").asOpt[Int].getOrElse(100)

            // Run the query
            executionType match {
                case _ => {
                    val rows = client.runQuery(query, fetchSize)

                    // Go over the rows and push them
                    for (row <- rows.get) flatten match {
                        case true => channel.push(new DataPacket(List(cassandra.rowToMap(row))))
                        case false => channel.push(new DataPacket(List(Map(resultName -> cassandra.rowToMap(row)))))
                    }
                }
            }
            
            // We stop once the query is done
            client.close()
            self ! StopPacket
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}