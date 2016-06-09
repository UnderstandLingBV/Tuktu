package tuktu.nosql.generators

import java.sql._
import akka.actor.ActorRef
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import tuktu.api._
import tuktu.nosql.util.sql
import tuktu.nosql.util.sql.ConnectionDefinition
import play.api.libs.iteratee.Enumerator
import anorm.Row
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

            // Build the enumerator to query SQL
            val rowEnumerator = sql.streamResult(query)(connection).andThen(Enumerator.eof)
            // Stop packet upon termination
            val onEOF = Enumeratee.onEOF[Row](() => {
                sql.releaseConnection(conn)
                self ! new StopPacket
            })
            // Enumeratee to turn the Row into a DP
            val rowToDP: Enumeratee[Row, DataPacket] = Enumeratee.mapM(row => Future { flatten match {
                case true => new DataPacket(List(sql.rowToMap(row)))
                case false => new DataPacket(List(Map(resultName -> sql.rowToMap(row))))
            }})
            
            // Chain together
            processors.foreach(processor => {
                rowEnumerator |>> (onEOF compose rowToDP compose processor) &>> sinkIteratee
            })
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}