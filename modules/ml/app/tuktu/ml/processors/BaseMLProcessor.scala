package tuktu.ml.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils
import tuktu.ml.models.GetModel
import scala.concurrent.Await
import tuktu.ml.models.BaseModel
import tuktu.ml.models.UpsertModel
import tuktu.ml.models.DestroyModel

/**
 * Abstract class that trains an ML model, supervised or unsupervised
 */
abstract class BaseMLTrainProcessor[BM <: BaseModel](resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var modelName = ""
    var destroyOnEOF = true

    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        destroyOnEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Ask model repository for this model
        val modelFut = Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ? new GetModel(modelName)
        // We cannot but wait here
        val model = Await.result(modelFut, timeout.duration).asInstanceOf[Option[BM]]
        val modelInstance = model match {
            case None => {
                // No model was found, create it and send it to our repository
                val model = instantiate
                Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new UpsertModel(modelName, model)

                model
            }
            case Some(m) => m
        }

        // Train our model
        val newModel = train(data.data, modelInstance)

        // Write back to our model repository
        Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new UpsertModel(modelName, newModel)

        data
    }) compose Enumeratee.onEOF(() => destroyOnEOF match {
        case true => {
            // Send model repository the signal to clean up the model
            Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new DestroyModel(modelName)
        }
        case _ => {}
    })

    /**
     * Instantiates the model
     */
    def instantiate(): BM = ???

    /**
     * Trains the model
     */
    def train(data: List[Map[String, Any]], model: BM): BM = ???
}

/**
 * Once a model has been trained and put available, this class can apply it
 */
abstract class BaseMLApplyProcessor[BM <: BaseModel](resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var modelName = ""
    var destroyOnEOF = true

    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        destroyOnEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Ask model repository for this model
        val modelFut = Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ? new GetModel(modelName)
        // We cannot but wait here
        val model = Await.result(modelFut, timeout.duration).asInstanceOf[Option[BM]]

        // Model cannot be null
        model match {
            case Some(m) => new DataPacket(applyModel(resultName, data.data, m))
            case None    => data
        }
    }) compose Enumeratee.onEOF(() => destroyOnEOF match {
        case true => {
            // Send model repository the signal to clean up the model
            Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new DestroyModel(modelName)
        }
        case _ => {}
    })

    /**
     * Applies the model to our data and return the results
     */
    def applyModel(resultName: String, data: List[Map[String, Any]], model: BM): List[Map[String, Any]] = ???
}

/**
 * Serializes a model
 */
class MLSerializeProcessor[BM <: BaseModel](resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var modelName = ""
    var fileName = ""
    var destroyOnEOF = true
    var onlyOnce = true
    var isSerialized = false
    
    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        fileName = (config \ "file_name").as[String]
        destroyOnEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
        onlyOnce = (config \ "only_once").asOpt[Boolean].getOrElse(true)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Check if we actually need to serialize
        if (!onlyOnce || !isSerialized) {
            // Ask model repository for this model
            val modelFut = Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ? new GetModel(modelName)
            // We cannot but wait here
            val model = Await.result(modelFut, timeout.duration).asInstanceOf[Option[BM]]
    
            // Model cannot be null
            model match {
                case Some(m) => {
                    isSerialized = true
                    m.serialize(fileName)
                }
                case None    => {}
            }
        }
        
        data
    }) compose Enumeratee.onEOF(() => destroyOnEOF match {
        case true => {
            // Send model repository the signal to clean up the model
            Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new DestroyModel(modelName)
        }
        case _ => {}
    })
}

/**
 * Deerializes a model
 */
abstract class BaseMLDeserializeProcessor[BM <: BaseModel](resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var modelName = ""
    var fileName = ""
    var onlyOnce = true
    var isDeserialized = false
    
    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        fileName = (config \ "file_name").as[String]
        onlyOnce = (config \ "only_once").asOpt[Boolean].getOrElse(true)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Check if we actually need to deserialize
        if (!onlyOnce || !isDeserialized) {
            // Deserialize the model
            val model = deserializeModel()
            
            // Send it to the repository
            Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new UpsertModel(modelName, model)
        }
        
        data
    })
    
    def deserializeModel(): BM = ???
}