package tuktu.nosql.generators

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import play.api.libs.iteratee._
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.Logger
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import reactivemongo.api.commands.Command
import reactivemongo.api.Cursor
import reactivemongo.api.MongoDriver
import reactivemongo.api.QueryOpts
import reactivemongo.api.ReadPreference
import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import tuktu.api.BaseGenerator
import tuktu.api.DataPacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import tuktu.nosql.util.MongoCollectionPool
import tuktu.nosql.util.MongoPipelineTransformer
import tuktu.nosql.util.MongoSettings

class MongoDBGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
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
            val settings = MongoSettings(hosts, database, coll)
            val collection = user match {
                case None => MongoCollectionPool.getCollection(settings)
                case Some(usr) => {
                    val credentials = admin match {
                        case true  => Authenticate("admin", usr, pwd)
                        case false => Authenticate(database, usr, pwd)
                    }
                    MongoCollectionPool.getCollectionWithCredentials(settings, credentials, scramsha1)
                    
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
                val resultData = limit match {
                    case Some(s) => collection.find(query, filter).sort(sort)
                        .options(QueryOpts().batchSize(s)).cursor[JsObject].collect[List](s)
                    case None => collection.find(query, filter).sort(sort)
                        .options(QueryOpts().batchSize(batchSize)).cursor[JsObject].collect[List]()
                }
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
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}

/**
 * Generator for MongoDB aggregations
 */
class MongoDBAggregateGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
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
            val settings = MongoSettings(hosts, database, coll)
            implicit val collection = user match {
                case None => MongoCollectionPool.getCollection(settings)
                case Some(usr) => {
                    val credentials = admin match {
                        case true  => Authenticate("admin", usr, pwd)
                        case false => Authenticate(database, usr, pwd)
                    }
                    MongoCollectionPool.getCollectionWithCredentials(settings, credentials, scramsha1)
                }
            }

            // Get tasks
            val tasks = (config \ "tasks").as[List[JsObject]]

            // Batch all the results before pushing it on the channel
            val batch = (config \ "batch").asOpt[Boolean].getOrElse(false)

            // prepare aggregation pipeline
            import collection.BatchCommands.AggregationFramework.PipelineOperator
            val transformer: MongoPipelineTransformer = new MongoPipelineTransformer()(collection)
            val pipeline: List[PipelineOperator] = tasks.map { x => transformer.json2task(x) }

            val resultFuture = {
                // Get data based on the aggregation pipeline
                import collection.BatchCommands.AggregationFramework.AggregationResult
                val resultData: Future[List[JsObject]] = collection.aggregate(pipeline.head, pipeline.tail).map(_.result[JsObject])
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
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
}

/**
 * Generator for MongoDB finds
 */
class MongoDBFindGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
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
            val settings = MongoSettings(hosts, database, coll)
            val collection = user match {
                case None => MongoCollectionPool.getCollection(settings)
                case Some(usr) => {
                    val credentials = admin match {
                        case true  => Authenticate("admin", usr, pwd)
                        case false => Authenticate(database, usr, pwd)
                    }
                    MongoCollectionPool.getCollectionWithCredentials(settings, credentials, scramsha1)
                    
                }
            }

            // Get query and filter
            val query = (config \ "query").as[JsObject]
            val filter = (config \ "filter").asOpt[JsObject].getOrElse(Json.obj())
            val sort = (config \ "sort").asOpt[JsObject].getOrElse(Json.obj())
            
            val enumerator: Enumerator[JsObject] = collection.find(query, filter).sort(sort).cursor[JsObject](ReadPreference.nearest).enumerate()
            val pushRecords: Iteratee[JsObject, Unit] = Iteratee.foreach { record => channel.push(new DataPacket(List(tuktu.api.utils.JsObjectToMap(record))))}                
            val future = enumerator.run(pushRecords)
            future.onSuccess {
                case _ => self ! new StopPacket
            }
            future.onFailure {
                case e: Throwable => {
                    e.printStackTrace
                    self ! new StopPacket
                }
            }         
        }
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
    
}

/**
 * A Generator to list the collections in a database
 */
class MongoDBCollectionsGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get connection properties
            val dbHosts = (config \ "hosts").as[List[String]]
            val dbName = (config \ "database").as[String]
            
            // Get credentials
            val user = (config \ "user").asOpt[String]
            val pwd = (config \ "password").asOpt[String].getOrElse("")
            val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
            
            // Set up connection
            val driver = new MongoDriver
            val connection = user match{
                case None => driver.connection(dbHosts)
                case Some( usr ) => {
                    val credentials = admin match{
                        case true => Seq(Authenticate("admin", usr, pwd))
                        case false => Seq(Authenticate(dbName, usr, pwd))
                    }
                driver.connection(dbHosts,authentications = credentials)  
                }
          }
          val db = connection(dbName)

          // Get command
          val command = Json.obj( "listCollections" -> 1 )
          val runner = Command.run(JSONSerializationPack)
          val futureResult = runner.apply(db, runner.rawCommand(command)).one[JsObject]
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
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
    
}

/**
 * A Generator to run a raw database command
 */
class MongoDBCommandGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    override def receive() = {
        case config: JsValue => {
            // Get connection properties
            val dbHosts = (config \ "hosts").as[List[String]]
            val dbName = (config \ "database").as[String]
            
            // Get credentials
            val user = (config \ "user").asOpt[String]
            val pwd = (config \ "password").asOpt[String].getOrElse("")
            val admin = (config \ "admin").asOpt[Boolean].getOrElse(true)
            
            // Set up connection
            val driver = new MongoDriver
            val connection = user match{
                case None => driver.connection(dbHosts)
                case Some( usr ) => {
                    val credentials = admin match{
                        case true => Seq(Authenticate("admin", usr, pwd))
                        case false => Seq(Authenticate(dbName, usr, pwd))
                    }
                driver.connection(dbHosts,authentications = credentials)  
                }
          }
          val db = connection(dbName)

          // Get command
          val command = (config \ "command").as[JsObject]
          val runner = Command.run(JSONSerializationPack)
          val futureResult = runner.apply(db, runner.rawCommand(command)).one[JsObject]
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
        case sp: StopPacket => cleanup
        case ip: InitPacket => setup
    }
    
}