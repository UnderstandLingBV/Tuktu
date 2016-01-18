package tuktu.nosql.generators

import java.sql._
import akka.actor.ActorRef
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api._
import tuktu.nosql.util.sql
import tuktu.nosql.util.sql.ConnectionDefinition

case class StopHelper(
        conn: ConnectionDefinition
)

class SQLGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
            val url = (config \ "url").as[String]
            val user = (config \ "user").as[String]
            val password = (config \ "password").as[String]
            val query = (config \ "query").as[String]
            val driver = (config \ "driver").as[String]
            
            // Do we need to flatten or not?
            val flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)

            // Load the driver, set up the client
            val conn = ConnectionDefinition(url, user, password, driver)
            val connection = sql.getConnection(conn)

            // Run the query
            val rows = sql.queryResult(query)(connection)
            for (row <- rows) flatten match {
                case true => channel.push(new DataPacket(List(sql.rowToMap(row))))
                case false => channel.push(new DataPacket(List(Map(resultName -> sql.rowToMap(row)))))
            }

            // We stop once the query is done
            self ! StopHelper(conn)
        }
        case sh: StopHelper => {
            sql.releaseConnection(sh.conn)
            cleanup
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}