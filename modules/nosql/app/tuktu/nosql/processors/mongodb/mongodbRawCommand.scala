package tuktu.nosql.processors.mongodb

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.modules.reactivemongo.json._
import reactivemongo.api._
import reactivemongo.api.commands.Command
import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.api.utils.{ MapToJsObject, evaluateTuktuString }

/**
 * Provides a helper to run specified database commands (as long as the command result is less than 16MB in size).
 */

class MongoDBRawCommandProcessor(resultName: String) extends BaseProcessor(resultName) {
    var command: String = _
    var db: DefaultDB = _
    var resultOnly: Boolean = _

    override def initialize(config: JsObject) {
        // Prepare db connection
        val dbHosts = (config \ "hosts").as[List[String]]
        val dbName = (config \ "database").as[String]
        
        // Get credentials
        // http://reactivemongo.org/releases/0.11/documentation/tutorial/connect-database.html
        val user = (config \ "user").asOpt[String]
        val pwd = (config \ "password").asOpt[String].getOrElse("")
        val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
        // val scramsha1 = (config \ "ScramSha1").as[Boolean]
        
        val driver = new MongoDriver
        val connection = user match{
            case None => driver.connection(dbHosts)
            case Some( usr ) => {
                val credentials = admin match{
                    case true => Seq(Authenticate("admin", usr, pwd))
                    case false => Seq(Authenticate(dbName, usr, pwd))
                }
                val conOpts = MongoConnectionOptions( authMode = ScramSha1Authentication )
                driver.connection(dbHosts,authentications = credentials, options = conOpts)  
            }
        }
        // TODO find a better way to wait for authentication to succeed
        // should be solved by upgrading to reactivemongo 0.11.9 (which requires play 2.4) with which authentication returns a Future that can be handled explicitly
        Thread.sleep(10000L)
        db = connection(dbName)

        // Get command
        command = (config \ "command").as[String]

        // Get result format
        resultOnly = (config \ "resultOnly").asOpt[Boolean].getOrElse(false)

    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
      val temp: List[Future[Map[String,Any]]] = data.data.map{ datum =>
        val runner = Command.run(JSONSerializationPack)
        val jcommand = Json.parse( evaluateTuktuString(command, datum) ).as[JsObject]
        val futureResult = runner.apply(db, runner.rawCommand(jcommand)).one[JsObject]
        futureResult.map{ result =>
            if (resultOnly) {
                datum + (resultName -> (result \ "result"))
            } else {
                datum + (resultName -> result)
            }
        }
        
      }
      val tmp = Future.sequence( temp )
      tmp.map{ l => new DataPacket( l ) }

    })

}