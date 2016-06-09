package tuktu.nosql.processors.mongodb

import akka.util.Timeout
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.Play.current
import play.modules.reactivemongo.json._
import reactivemongo.api._
import reactivemongo.api.commands.Command
import reactivemongo.core.commands.SuccessfulAuthentication
import reactivemongo.core.nodeset.Authenticate
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.api.utils.{ MapToJsObject, evaluateTuktuString }

/**
 * Provides a helper to run specified database commands (as long as the command result is less than 16MB in size).
 */
class MongoDBRawCommandProcessor(resultName: String) extends BaseProcessor(resultName)
{
    var command: String = _
    var db: DefaultDB = _
    var auth: Option[Future[SuccessfulAuthentication]] = _
    var resultOnly: Boolean = _
    var connection: MongoConnection = _
    
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

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
        
        def typesafeConfig: com.typesafe.config.Config = play.api.libs.concurrent.Akka.system.settings.config
        val driver = new MongoDriver(Some(typesafeConfig))
        
        connection = user match{
            case None => driver.connection(dbHosts)
            case Some( u ) => driver.connection(dbHosts, options = MongoConnectionOptions( authMode = ScramSha1Authentication ))
        }
        
        db = connection( dbName )
        auth = user match {
            case None => None
            case Some( u ) => Option({ admin match {
                case false => db.authenticate(u, pwd)(timeout.duration)
                case true => connection( "admin" ).authenticate(u, pwd)(timeout.duration) }})
        }
        
        // Get command
        command = (config \ "command").as[String]

        // Get result format
        resultOnly = (config \ "resultOnly").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        val lfuture: List[Future[Map[String,Any]]] = data.data.map{ datum =>
            val runner = Command.run(JSONSerializationPack)
            val jcommand = Json.parse( evaluateTuktuString(command, datum) ).as[JsObject]
            val futureResult: Future[JsObject] = auth match{
                case None => runner.apply(db, runner.rawCommand(jcommand)).one[JsObject]
                case Some( a ) => a.flatMap { _ => runner.apply(db, runner.rawCommand(jcommand)).one[JsObject] }
            }
            futureResult.map{ result =>
                if (resultOnly) 
                {
                    datum + (resultName -> (result \ "result"))
                } 
                else 
                {
                    datum + (resultName -> result)
                }
            }  
        }
        Future.sequence( lfuture ).map{ list => new DataPacket( list ) }
    })

}