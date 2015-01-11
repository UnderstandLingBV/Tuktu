package tuktu.nosql.generators

import tuktu.api._
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import java.sql._
import anorm._
import tuktu.nosql.util.sql

class SQLGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]]) extends AsyncGenerator(resultName, processors) {
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
            val client = new sql.client(url, user, password, driver)

            // Run the query
            val rows = client.queryResult(query)
            for (row <- rows) flatten match {
                case true => channel.push(new DataPacket(List(sql.rowToMap(row))))
                case false => channel.push(new DataPacket(List(Map(resultName -> sql.rowToMap(row)))))
            }

            // We stop once the query is done
            client.close
            self ! StopPacket
        }
        case sp: StopPacket => {
            cleanup()
        }
    }
}