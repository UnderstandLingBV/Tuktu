package tuktu.nosql.processors.caching

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.nosql.util.sql.client
import tuktu.nosql.util.stringHandler

/**
 * Create a clone of a remote Database to an in memory H2 Database.
 */
class H2Caching (resultName: String) extends BaseProcessor(resultName) {
    var sqlClient: client = _
    var h2Client: client = _
    var dbName: String = _
    var tables: List[String] = _

    override def initialize(config: JsObject) {
        // Get url, username and password for the connection; and the SQL driver (new drivers may have to be added to dependencies) and query
        val url = (config \ "url").as[String]
        val user = (config \ "user").as[String]
        val password = (config \ "password").as[String]
        val driver = (config \ "driver").as[String]
        
        // Set up the client
        sqlClient = new client(url, user, password, driver)
        
        // Database to clone
        dbName = (config \ "db_name").as[String]
        // Tables to clone
        tables = (config \ "tables").as[List[String]]
        
        //create a H2 Client
        h2Client = createH2Client
        
        //clean Database
        h2Client.query("DROP ALL OBJECTS")

        //for each table, request table structure and copy over all data
        for (table <- tables) {
            // request table info and recreate
            val createTable = sqlClient.queryResult(s"SHOW CREATE TABLE $dbName.$table").head[String]("Create Table")
            // clean up character encodings
            val createTableCleanedUp = createTable.replaceAll("(?i)character set [^ ]*", "").replaceAll("(?i)default charset=[^ ]*","")
            // execute
            h2Client.query(createTableCleanedUp)

            //copy over each row to h2 db
            for (row <- sqlClient.queryResult(s"SELECT * FROM $dbName.$table")) { 
                h2Client.query(stringHandler.evaluateString("INSERT INTO `" + table + "` VALUES (${values;,})", Map("values" -> row.asList)))    
            }
        }
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
         data
    })
    
    def createH2Client() = {
        val url = s"jdbc:h2:mem:$dbName;MODE=MYSQL;DB_CLOSE_DELAY=-1;IGNORECASE=TRUE"
        val user = "sa"
        val password = ""
        val driver = "org.h2.Driver"
        
        new client(url, user, password, driver)
    }
}