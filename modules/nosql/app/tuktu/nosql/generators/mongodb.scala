package tuktu.nosql.generators

import akka.actor.ActorRef
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Input
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.JsValue
import play.api.Logger
import play.api.Play.current
import play.modules.reactivemongo.json.JsObjectDocumentWriter
import play.modules.reactivemongo.json.JSONSerializationPack
import reactivemongo.api.commands.Command
import reactivemongo.api.MongoConnection
import reactivemongo.api.MongoConnectionOptions
import reactivemongo.api.MongoDriver
import reactivemongo.api.QueryOpts
import reactivemongo.api.ReadPreference
import reactivemongo.api.ScramSha1Authentication
import reactivemongo.core.commands.SuccessfulAuthentication
import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import tuktu.nosql.util.MongoCollectionPool
import tuktu.nosql.util.MongoPipelineTransformer
import tuktu.nosql.util.MongoSettings
import tuktu.nosql.util.MongoTools

class MongoDBGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) 
{    
    var settings: MongoSettings = _
    override def receive() = {
        case config: JsValue => {
            // Get connection properties
            val hosts = (config \ "hosts").as[List[String]]
            val database = (config \ "database").as[String]
            val coll = (config \ "collection").as[String]

            // Get credentials
            val user = (config \ "user").asOpt[String]
            val pwd = (config \ "password").asOpt[String].getOrElse("")
            val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
            val scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

            // Set up connection
            settings = MongoSettings(hosts, database, coll)
            val fcollection = user match {
                case None =>MongoTools.getFutureCollection(this, settings)
                case Some(usr) => {
                    val credentials = admin match {
                        case true  => Authenticate("admin", usr, pwd)
                        case false => Authenticate(database, usr, pwd)
                    }
                    MongoTools.getFutureCollection(this, settings, credentials, scramsha1)
                }
            }

            // Get query and filter
            val query = (config \ "query").as[JsObject]
            val filter = (config \ "filter").asOpt[JsObject].getOrElse(Json.obj())
            val sort = (config \ "sort").asOpt[JsObject].getOrElse(Json.obj())

            // Batch all the results before pushing it on the channel
            val batch = (config \ "batch").asOpt[Boolean].getOrElse(false)
            val limit = (config \ "limit").asOpt[Int]
            val batchSize = (config \ "batch_size").asOpt[Int].getOrElse(50)

            val resultFuture = {
                // Get data based on query and filter
                val resultData = fcollection.flatMap { collection =>  limit match {
                    case Some(s) => collection.find(query, filter).sort(sort)
                        .options(QueryOpts().batchSize(s)).cursor[JsObject](ReadPreference.primary).collect[List](s)
                    case None => collection.find(query, filter).sort(sort)
                        .options(QueryOpts().batchSize(batchSize)).cursor[JsObject](ReadPreference.primary).collect[List]()
                }}
                // Get futures into JSON
                resultData.map { resultList =>
                    {
                        for (resultRow <- resultList) yield {
                            tuktu.api.utils.JsObjectToMap(resultRow)
                        }
                    }
                }
            }

            // Handle results
            resultFuture.onSuccess {
                case res: List[Map[String, Any]] => {
                    // Determine what to do based on batch or non batch
                    if (batch)
                        channel.push(new DataPacket(res))
                    else
                        res.foreach(row => channel.push(new DataPacket(List(row))))

                    self ! new StopPacket()
                }
                case _ => self ! new StopPacket()
            }
            resultFuture.onFailure {
                case e: Throwable => {
                    Logger.error("Error executing MongoDB Query", e)
                    self ! new StopPacket
                }
            }
        }
        case sp: StopPacket => MongoTools.deleteCollection(this, settings); cleanup
        case ip: InitPacket => setup
    }
}

/**
 * Generator for MongoDB aggregations
 */
class MongoDBAggregateGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) 
{
    var settings: MongoSettings = _
    override def receive() = 
    {
        case config: JsValue => {
            // Get connection properties
            val hosts = (config \ "hosts").as[List[String]]
            val database = (config \ "database").as[String]
            val coll = (config \ "collection").as[String]

            // Get credentials
            val user = (config \ "user").asOpt[String]
            val pwd = (config \ "password").asOpt[String].getOrElse("")
            val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
            val scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

            // Set up connection
            settings = MongoSettings(hosts, database, coll)
            implicit val collection = user match {
                case None => MongoTools.getFutureCollection(this, settings) 
                case Some(usr) => {
                    val credentials = admin match {
                        case true  => Authenticate("admin", usr, pwd)
                        case false => Authenticate(database, usr, pwd)
                    }
                    MongoTools.getFutureCollection(this, settings, credentials, scramsha1)
                }
            }

            // Get tasks
            val tasks = (config \ "tasks").as[List[JsObject]]

            // Batch all the results before pushing it on the channel
            val batch = (config \ "batch").asOpt[Boolean].getOrElse(false)

            // prepare aggregation pipeline
            val resultFuture = collection.flatMap { coll => {
                import coll.BatchCommands.AggregationFramework.PipelineOperator
                val transformer: MongoPipelineTransformer = new MongoPipelineTransformer()(coll)
                val pipeline: List[PipelineOperator] = tasks.map { x => transformer.json2task(x)(collection=coll) }
                // Get data based on the aggregation pipeline
                import coll.BatchCommands.AggregationFramework.AggregationResult
                val resultData: Future[List[JsObject]] = coll.aggregate(pipeline.head, pipeline.tail).map(_.result[JsObject])
                // Get futures into JSON
                resultData.map { resultList =>
                {
                    for (resultRow <- resultList) yield {
                        tuktu.api.utils.JsObjectToMap(resultRow)
                    }
                }}
            }}
            // Handle results
            resultFuture.onSuccess {
                case res: List[Map[String, Any]] => {
                    // Determine what to do based on batch or non batch
                    if (batch)
                        channel.push(new DataPacket(res))
                    else
                        res.foreach(row => channel.push(new DataPacket(List(row))))
                }
                case _ => self ! new StopPacket()
            }
            resultFuture.onFailure {
                case e: Throwable => {
                    e.printStackTrace
                    self ! new StopPacket
                }
            }
        }
        case sp: StopPacket => MongoTools.deleteCollection(this, settings); cleanup
        case ip: InitPacket => setup
    }
}

/**
 * Generator for MongoDB finds
 */
class MongoDBFindGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) 
{
    var settings: MongoSettings = _
    override def receive() = {
        case config: JsValue => {
            // Get connection properties
            val hosts = (config \ "hosts").as[List[String]]
            val database = (config \ "database").as[String]
            val coll = (config \ "collection").as[String]

            // Get credentials
            val user = (config \ "user").asOpt[String]
            val pwd = (config \ "password").asOpt[String].getOrElse("")
            val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
            val scramsha1 = (config \ "ScramSha1").asOpt[Boolean].getOrElse(true)

            // Set up connection
            settings = MongoSettings(hosts, database, coll)
            val fcollection = user match {
                case None => MongoTools.getFutureCollection(this, settings)
                case Some(usr) => {
                    val credentials = admin match {
                        case true  => Authenticate("admin", usr, pwd)
                        case false => Authenticate(database, usr, pwd)
                    }
                    MongoTools.getFutureCollection(this, settings, credentials, scramsha1) 
                }
            }
            // Get query and filter
            val query = (config \ "query").as[JsObject]
            val filter = (config \ "filter").asOpt[JsObject].getOrElse(Json.obj())
            val sort = (config \ "sort").asOpt[JsObject].getOrElse(Json.obj())
            
            fcollection.map{ collection =>
                // Create the enumerator that gets JsObjects
                val enumerator: Enumerator[JsObject] = collection.find(query, filter)
                    .sort(sort).cursor[JsObject](ReadPreference.nearest)
                    .enumerate().andThen(Enumerator.eof)
                // Transformator to turn the JsObjects into DataPackets
                val transformator: Enumeratee[JsObject, DataPacket] = Enumeratee.mapM(record => Future {DataPacket(List(tuktu.api.utils.JsObjectToMap(record)))})
                // onEOF close the reader and send StopPacket
                val onEOF = Enumeratee.onEOF[DataPacket](() => self ! new StopPacket)
                
                // Chain this together
                processors.foreach(processor => {
                    enumerator |>> (transformator compose onEOF compose processor) &>> sinkIteratee
                })
            }
        }
        case sp: StopPacket => MongoTools.deleteCollection(this, settings); cleanup
        case ip: InitPacket => setup
    }
}

/**
 * A Generator to list the collections in a database (requires MongoDB 3.0 or higher)
 */
class MongoDBCollectionsGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) 
{
    var connection: reactivemongo.api.MongoConnection = _
    
    override def receive() = {
        case config: JsValue => {
            // Get connection properties
            val dbHosts = (config \ "hosts").as[List[String]]
            val dbName = (config \ "database").as[String]
            
            // Get credentials
            val user = (config \ "user").asOpt[String]
            val pwd = (config \ "password").asOpt[String].getOrElse("")
            val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
            
            // Authenticate and set up connection
            def typesafeConfig: com.typesafe.config.Config = play.api.libs.concurrent.Akka.system.settings.config
            val driver = new reactivemongo.api.MongoDriver(Some(typesafeConfig))
            
            connection = user match{
                case None => driver.connection(dbHosts)
                case Some( u ) => driver.connection(dbHosts, options = MongoConnectionOptions( authMode = ScramSha1Authentication ))
            }
            
            val db = connection(dbName)
            val auth = user match {
                case None => None
                case Some( u ) => Option({ admin match {
                    case false => db.authenticate(u, pwd)(timeout.duration)
                    case true => connection( "admin" ).authenticate(u, pwd)(timeout.duration) }})
            }

            // Get command
            val command = Json.obj( "listCollections" -> 1 )
            val runner = Command.run(JSONSerializationPack)
            
            val futureResult: Future[JsObject] = auth match{
                case None => runner.apply(db, runner.rawCommand(command)).one[JsObject]
                case Some( a ) => a.flatMap { _ => runner.apply(db, runner.rawCommand(command)).one[JsObject] }
            }
            
            val futureCollections = futureResult.map{ result => (result \\ "name").map { coll => coll.as[String] } }
            futureCollections.onSuccess {
                case collections: List[String] => collections.foreach{ collection => channel.push(new DataPacket(List( Map(resultName -> collection) ))) } 
                case _ => self ! new StopPacket
            }
            futureCollections.onFailure {
                case e: Throwable => {
                    e.printStackTrace
                    self ! new StopPacket
                }
            }         
        }
        case sp: StopPacket => connection.close; cleanup
        case ip: InitPacket => setup
    }
}

/**
 * A Generator to run a raw database command
 */
class MongoDBCommandGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) 
{
    var connection: MongoConnection = _
    override def receive() = {
        case config: JsValue => {
            // Get connection properties
            val dbHosts = (config \ "hosts").as[List[String]]
            val dbName = (config \ "database").as[String]
            
            // Get credentials
            val user = (config \ "user").asOpt[String]
            val pwd = (config \ "password").asOpt[String].getOrElse("")
            val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
            
            // Authenticate and set up connection
            def typesafeConfig: com.typesafe.config.Config = play.api.libs.concurrent.Akka.system.settings.config
            val driver = new reactivemongo.api.MongoDriver(Some(typesafeConfig))
            
            connection = user match{
                case None => driver.connection(dbHosts)
                case Some( u ) => driver.connection(dbHosts, options = MongoConnectionOptions( authMode = ScramSha1Authentication ))
            }
            
            val db = connection(dbName)
            val auth = user match {
                case None => None
                case Some( u ) => Option({ admin match {
                    case false => db.authenticate(u, pwd)(timeout.duration)
                    case true => connection( "admin" ).authenticate(u, pwd)(timeout.duration) }})
            }

          // Get command
          val command = (config \ "command").as[JsObject]
          val runner = Command.run(JSONSerializationPack)
          
          val futureResult: Future[JsObject] = auth match{
                case None => runner.apply(db, runner.rawCommand(command)).one[JsObject]
                case Some( a ) => a.flatMap { _ => runner.apply(db, runner.rawCommand(command)).one[JsObject] }
            }
          
          futureResult.onSuccess {
              case result: JsObject => channel.push(new DataPacket(List( Map(resultName -> result) )))
              case _ => self ! new StopPacket
          }
          futureResult.onFailure {
                case e: Throwable => {
                    e.printStackTrace
                    self ! new StopPacket
                }
            }         
        }
        case sp: StopPacket => connection.close; cleanup
        case ip: InitPacket => setup
    }
}