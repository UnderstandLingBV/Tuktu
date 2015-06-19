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
 * Abstract class that trains an ML model, supervised or unsupverised
 */
abstract class BaseMLTrainProcessor[BM <: BaseModel](resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    
    var modelName = ""
    var destroyOnEOF = true
    
    override def initialize(config: JsObject) = {
        modelName = (config \ "model_name").as[String]
        destroyOnEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map((data: DataPacket) => {
        // Ask model repository for this model
        val modelFut = Akka.system.actorSelection("user/TuktuMonitor") ? new GetModel(modelName)
        // We cannot but wait here
        val model = Await.result(modelFut, timeout.duration).asInstanceOf[BM]
        val modelInstance = if (model == null) {
            // No model was found, create it and send it to our repository
            val model = instantiate
            Akka.system.actorSelection("user/TuktuMonitor") ! new UpsertModel(modelName, model)
            
            model
        }
        else {
            // Model exists
            model
        }
        
        // Train our model
        val newModel = train(data.data, modelInstance)
        
        // Write back to our model repository
        Akka.system.actorSelection("user/TuktuMonitor") ! new UpsertModel(modelName, newModel)
        
        data
    }) compose Enumeratee.onEOF(() => destroyOnEOF match {
        case true => {
            // Send model repository the signal to clean up the model
            Akka.system.actorSelection("user/TuktuMonitor") ! new DestroyModel(modelName)
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
    
    override def initialize(config: JsObject) = {
        modelName = (config \ "model_name").as[String]
        destroyOnEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Ask model repository for this model
        val modelFut = Akka.system.actorSelection("user/TuktuMonitor") ? new GetModel(modelName)
        // We cannot but wait here
        val model = Await.result(modelFut, timeout.duration).asInstanceOf[BM]
        
        // Model cannot be null
        if (model != null) {
            // Apply our model
            new DataPacket(applyModel(resultName, data.data, model))
        } else data
    }) compose Enumeratee.onEOF(() => destroyOnEOF match {
        case true => {
            // Send model repository the signal to clean up the model
            Akka.system.actorSelection("user/TuktuMonitor") ! new DestroyModel(modelName)
        }
        case _ => {}
    })
    
    /**
     * Applies the model to our data and return the results
     */
    def applyModel(resultName: String, data: List[Map[String, Any]], model: BM): List[Map[String, Any]] = ???
}