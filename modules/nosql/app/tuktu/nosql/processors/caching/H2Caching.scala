package tuktu.nosql.processors.caching

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.{ BaseProcessor, DataPacket }
import tuktu.nosql.util.sql._
import java.sql.Connection

/**
 * Create a clone of a remote Database to an in memory H2 Database.
 */
class H2Caching(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsObject) {
        // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
        val url = (config \ "url").as[String]
        val user = (config \ "user").as[String]
        val password = (config \ "password").as[String]
        val driver = (config \ "driver").as[String]

        // Set up the SQL client
        val sqlConnDef = new ConnectionDefinition(url, user, password, driver)
        val sqlConn = getConnection(sqlConnDef)

        // Database to clone
        val dbName = (config \ "db_name").as[String]
        // Tables to clone
        val tables = (config \ "tables").as[List[String]]

        //create a H2 Client
        val (h2Conn, h2ConnDef) = createH2Client(dbName)

        //clean Database
        tuktu.nosql.util.sql.query("DROP ALL OBJECTS")(h2Conn)

        //for each table, request table structure and copy over all data
        for (table <- tables) {
            // request table info and recreate
            val tmp = queryResult(s"SHOW CREATE TABLE $dbName.$table", sqlConnDef)(sqlConn)
            val createTable = tmp._1.head(".Create Table").asInstanceOf[String]
            // clean up character encodings
            val createTableCleanedUp = createTable.replaceAll("(?i)character set [^ ]*", "").replaceAll("(?i)default charset=[^ ]*", "")
            // execute
            tuktu.nosql.util.sql.query(createTableCleanedUp)(h2Conn)

            //copy over each row to h2 db
            for (row <- queryResult(s"SELECT * FROM $dbName.$table", sqlConnDef)(sqlConn)._1) {
                val list = row.toList
                tuktu.nosql.util.sql.query(
                    tuktu.api.utils.evaluateTuktuString(
                        "INSERT INTO `" + table + "` " +
                            list.map { case (key, _) => key.replaceAll("(?i)^" + java.util.regex.Pattern.quote(table + "."), "") }.mkString("(", ",", ")") + " VALUES " +
                            list.map { case (key, _) => "$SQL{" + key + "}" }.mkString("(", ",", ");"), row))(h2Conn)
            }
        }
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data
    })

    def createH2Client(dbName: String) = {
        val url = s"jdbc:h2:mem:$dbName;MODE=MYSQL;DB_CLOSE_DELAY=-1;IGNORECASE=TRUE"
        val user = "sa"
        val password = ""
        val driver = "org.h2.Driver"

        val connDef = new ConnectionDefinition(url, user, password, driver)
        val conn = getConnection(connDef)

        (conn, connDef)
    }
}