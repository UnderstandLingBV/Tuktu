package tuktu.nosql.generators

import akka.actor.ActorRef
import collection.immutable.SortedSet
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
import tuktu.nosql.util.MongoPipelineTransformer
import tuktu.nosql.util._
import reactivemongo.api.FailoverStrategy
import play.modules.reactivemongo.json.collection.JSONCollection

/**
 * Generator for MongoDB aggregations
 */
class MongoDBAggregateGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var conn: MongoConnection = _
    var nodes: List[String] = _
    
    override def _receive = {
        case config: JsValue => {
            // Get hosts
            nodes = (config \ "hosts").as[List[String]]
            // Get connection properties
            val opts = (config \ "mongo_options").asOpt[JsObject]
            val mongoOptions = MongoPool.parseMongoOptions(opts)
            // Get credentials
            val authentication = (config \ "auth").asOpt[JsObject]
            val auth = authentication match {
                case None => None
                case Some(a) => Some(Authenticate(
                        (a \ "db").as[String],
                        (a \ "user").as[String],
                        (a \ "password").as[String]
                ))
            }
            
            // Get the connection
            val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)

            // Get tasks
            val tasks = (config \ "tasks").as[List[JsObject]]

            // Batch all the results before pushing it on the channel
            val batch = (config \ "batch").asOpt[Boolean].getOrElse(false)

            // prepare aggregation pipeline
            val resultFuture = fConnection.flatMap {connection => 
                conn = connection
                val fCollection = MongoPool.getCollection(connection, (config \ "db").as[String], (config \ "collection").as[String])
                fCollection.onFailure {
                    case _ => self ! new StopPacket
                }
                fCollection.flatMap { collection: JSONCollection => {
                        import collection.BatchCommands.AggregationFramework.PipelineOperator
                        import collection.BatchCommands.AggregationFramework.AggregationResult
                        
                        val transformer: MongoPipelineTransformer = new MongoPipelineTransformer()(collection)
                        val pipeline: List[PipelineOperator] = tasks.map { x => transformer.json2task(x)(collection=collection) }
                        // Get data based on the aggregation pipeline
                        val resultData: Future[List[JsObject]] = collection.aggregate(pipeline.head, pipeline.tail).map(_.result[JsObject])
                        resultData.onFailure {
                            case _ => self ! new StopPacket
                        }
                        // Get futures into JSON
                        resultData.map {resultList =>
                            for (resultRow <- resultList) yield tuktu.api.utils.JsObjectToMap(resultRow)
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
                    e.printStackTrace
                    self ! new StopPacket
                }
            }
        }
        case sp: StopPacket => {
            if (conn != null)
                MongoPool.releaseConnection(nodes, conn)
            cleanup
        }
    }
}

/**
 * Generator for MongoDB finds
 */
class MongoDBFindGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var conn: MongoConnection = _
    var nodes: List[String] = _
    
    override def _receive = {
        case config: JsValue => {
            // Get hosts
            nodes = (config \ "hosts").as[List[String]]
            // Get connection properties
            val opts = (config \ "mongo_options").asOpt[JsObject]
            val mongoOptions = MongoPool.parseMongoOptions(opts)
            // Get credentials
            val authentication = (config \ "auth").asOpt[JsObject]
            val auth = authentication match {
                case None => None
                case Some(a) => Some(Authenticate(
                        (a \ "db").as[String],
                        (a \ "user").as[String],
                        (a \ "password").as[String]
                ))
            }
            
            // Get the connection
            val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
            
            // Get query and filter
            val query = (config \ "query").as[JsObject]
            val filter = (config \ "filter").asOpt[JsObject].getOrElse(Json.obj())
            val sort = (config \ "sort").asOpt[JsObject].getOrElse(Json.obj())
            
            // Continue when we have a connection set up
            fConnection.map { connection =>
                conn = connection
                val fCollection = MongoPool.getCollection(connection, (config \ "db").as[String], (config \ "collection").as[String])
                fCollection.onSuccess {
                    case collection: JSONCollection => {
                        val enumerator = collection.find(query, filter)
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
                    case _ => self ! new StopPacket
                }
                fCollection.onFailure {
                    case _ => self ! new StopPacket
                }
            }
        }
        case sp: StopPacket => {
            if (conn != null)
                MongoPool.releaseConnection(nodes, conn)
            cleanup(false)
        }
    }
}

/**
 * A Generator to list the collections in a database (requires MongoDB 3.0 or higher)
 */
class MongoDBCollectionsGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var conn: MongoConnection = _
    var nodes: List[String] = _
    
    override def _receive = {
        case config: JsValue => {
            // Get hosts
            nodes = (config \ "hosts").as[List[String]]
            // Get connection properties
            val opts = (config \ "mongo_options").asOpt[JsObject]
            val mongoOptions = MongoPool.parseMongoOptions(opts)
            // Get credentials
            val authentication = (config \ "auth").asOpt[JsObject]
            val auth = authentication match {
                case None => None
                case Some(a) => Some(Authenticate(
                        (a \ "db").as[String],
                        (a \ "user").as[String],
                        (a \ "password").as[String]
                ))
            }
            
            // Get the connection
            val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
            fConnection.map { connection =>
                conn = connection
                // Get DB
                val fDb = connection.database((config \ "db").as[String])
                fDb.onFailure {
                    case _ => self ! new StopPacket
                }
                fDb.map {db => 
                    // Get command
                    val command = Json.obj("listCollections" -> 1)
                    val runner = Command.run(JSONSerializationPack)
                    // Run it
                    val futureResult = runner(db, runner.rawCommand(command)).one[JsObject]
                    
                    val futureCollections = futureResult.map{ result => (result \\ "name").map { coll => coll.as[String] } }
                    futureCollections.onSuccess {
                        case collections: List[String] => {
                            collections.foreach{ collection => channel.push(new DataPacket(List( Map(resultName -> collection) ))) }
                            self ! new StopPacket
                        }
                        case _ => self ! new StopPacket
                    }
                    futureCollections.onFailure {
                        case e: Throwable => {
                            e.printStackTrace
                            self ! new StopPacket
                        }
                    }
                }
            }
        }
        case sp: StopPacket => {
            if (conn != null)
                MongoPool.releaseConnection(nodes, conn)
            cleanup
        }
    }
}

/**
 * A Generator to run a raw database command
 */
class MongoDBCommandGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var conn: MongoConnection = _
    var nodes: List[String] = _
    
    override def _receive = {
        case config: JsValue => {
            // Get hosts
            nodes = (config \ "hosts").as[List[String]]
            // Get connection properties
            val opts = (config \ "mongo_options").asOpt[JsObject]
            val mongoOptions = MongoPool.parseMongoOptions(opts)
            // Get credentials
            val authentication = (config \ "auth").asOpt[JsObject]
            val auth = authentication match {
                case None => None
                case Some(a) => Some(Authenticate(
                        (a \ "db").as[String],
                        (a \ "user").as[String],
                        (a \ "password").as[String]
                ))
            }
            
            // Get the connection
            val fConnection = MongoPool.getConnection(nodes, mongoOptions, auth)
            fConnection.map { connection =>
                conn = connection
                // Get DB
                val fDb = connection.database((config \ "db").as[String])
                fDb.onFailure {
                    case _ => self ! new StopPacket
                }
                fDb.map {db => 
                    // Get command
                    val command = (config \ "command").as[JsObject]
                    val runner = Command.run(JSONSerializationPack)
                    // Run it
                    val futureResult = runner(db, runner.rawCommand(command)).one[JsObject]
                    
                    val futureCollections = futureResult.map{ result => (result \\ "name").map { coll => coll.as[String] } }
                    futureCollections.onSuccess {
                        case collections: List[String] => {
                            collections.foreach{ collection => channel.push(new DataPacket(List( Map(resultName -> collection) ))) }
                            self ! new StopPacket
                        }
                        case _ => self ! new StopPacket
                    }
                    futureCollections.onFailure {
                        case e: Throwable => {
                            e.printStackTrace
                            self ! new StopPacket
                        }
                    }
                }
            }
        }
        case sp: StopPacket => {
            if (conn != null)
                MongoPool.releaseConnection(nodes, conn)
            cleanup
        }
    }
}