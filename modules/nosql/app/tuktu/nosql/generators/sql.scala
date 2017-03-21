package tuktu.nosql.generators

import akka.actor.ActorRef
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api._
import java.sql.Connection
import tuktu.nosql.util.sql
import tuktu.nosql.util.sql.ConnectionDefinition

class SQLGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var connDef: ConnectionDefinition = _
    var conn: Connection = _

    override def _receive = {
        case config: JsValue =>
            // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
            val url = (config \ "url").as[String]
            val user = (config \ "user").as[String]
            val password = (config \ "password").as[String]
            val query = (config \ "query").as[String]
            val driver = (config \ "driver").as[String]

            // Do we need to flatten or not?
            val flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)

            // Load the driver, set up the client
            connDef = ConnectionDefinition(url, user, password, driver)
            conn = sql.getConnection(connDef)

            // Run the query and push the results
            val tmp = sql.queryResult(query, connDef)(conn)
            val rows = tmp._1
            conn = tmp._2
            if (flatten)
                for (row <- rows) channel.push(DataPacket(List(row)))
            else
                for (row <- rows) channel.push(DataPacket(List(Map(resultName -> row))))

            // We stop once the query is done
            self ! new StopPacket

        case sh: StopPacket =>
            sql.releaseConnection(connDef, conn)
            cleanup
    }
}