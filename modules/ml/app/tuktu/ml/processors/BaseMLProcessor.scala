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
import scala.concurrent.Await
import tuktu.ml.models.{ BaseModel, GetModel, ExistsModel, UpsertModel, DestroyModel }

/**
 * Abstract class that trains an ML model, supervised or unsupervised
 */
abstract class BaseMLTrainProcessor[BM <: BaseModel](resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var modelName = ""
    var waitForStore = false
    var destroyOnEOF = true
    val toBeDestroyed = collection.mutable.Set.empty[String]

    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        destroyOnEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
        waitForStore = (config \ "wait_for_store").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Get name of the model and line it up for destruction
        val newModelName = utils.evaluateTuktuString(modelName, data.data.headOption.getOrElse(Map.empty))
        if (destroyOnEOF) toBeDestroyed += newModelName

        // Ask model repository for this model
        val modelFut = Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ? new GetModel(newModelName)
        // We cannot but wait here
        val model = Await.result(modelFut, timeout.duration).asInstanceOf[Option[BM]]
        val modelInstance = model match {
            case None => {
                // No model was found, create it and send it to our repository
                val model = instantiate
                Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new UpsertModel(newModelName, model, false)

                model
            }
            case Some(m) => m
        }

        // Train our model
        val newModel = train(data.data, modelInstance)

        // Write back to our model repository
        if (waitForStore) {
            val fut = Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ? new UpsertModel(newModelName, newModel, true)
            Await.result(fut, timeout.duration)
        } else Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new UpsertModel(newModelName, newModel, false)

        data
    }) compose Enumeratee.onEOF(() => for (modelName <- toBeDestroyed) {
        // Send model repository the signal to clean up the model
        Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new DestroyModel(modelName)
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
    val toBeDestroyed = collection.mutable.Set.empty[String]

    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        destroyOnEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Get name of the model
        val newModelName = utils.evaluateTuktuString(modelName, data.data.headOption.getOrElse(Map.empty))

        // Ask model repository for this model
        val modelFut = Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ? new GetModel(newModelName)
        // We cannot but wait here
        val model = Await.result(modelFut, timeout.duration).asInstanceOf[Option[BM]]

        // Model cannot be null
        model match {
            case Some(m) => {
                // New Model Name will be used, so line it up for destruction
                if (destroyOnEOF) toBeDestroyed += newModelName
                // Apply model
                DataPacket(applyModel(resultName, data.data, m))
            }
            case None => data
        }
    }) compose Enumeratee.onEOF(() => for (modelName <- toBeDestroyed) {
        // Send model repository the signal to clean up the model
        Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new DestroyModel(modelName)
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
    val serialized = collection.mutable.Set.empty[String]

    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        fileName = (config \ "file_name").as[String]
        destroyOnEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
        onlyOnce = (config \ "only_once").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Get name of the model
        val newModelName = utils.evaluateTuktuString(modelName, data.data.headOption.getOrElse(Map.empty))

        // Check if we actually need to serialize
        if (!onlyOnce || !serialized.contains(newModelName)) {
            // Ask model repository for this model
            val modelFut = Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ? new GetModel(newModelName)
            // We cannot but wait here
            val model = Await.result(modelFut, timeout.duration).asInstanceOf[Option[BM]]

            // Model cannot be null
            model match {
                case Some(m) => {
                    serialized += newModelName
                    m.serialize(fileName)
                }
                case None => {}
            }
        }

        data
    }) compose Enumeratee.onEOF(() => if (destroyOnEOF) {
        for (modelName <- serialized) {
            // Send model repository the signal to clean up the model
            Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new DestroyModel(modelName)
        }
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
    var waitForLoad = false

    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        fileName = (config \ "file_name").as[String]
        onlyOnce = (config \ "only_once").asOpt[Boolean].getOrElse(true)
        waitForLoad = (config \ "wait_for_load").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        // Check if we actually need to deserialize
        if (data.data.nonEmpty) {
            // Get name of the model
            val newModelName = utils.evaluateTuktuString(modelName, data.data.head)

            // Check if we need to deserialize this model
            val toDeserialize = !onlyOnce || {
                // Check if model is already deserialized
                val isDeserialized = Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ? new ExistsModel(newModelName)
                !Await.result(isDeserialized, timeout.duration).asInstanceOf[Boolean]
            }

            if (toDeserialize) {
                // Get file name of the model
                val newFileName = utils.evaluateTuktuString(fileName, data.data.head)

                // Deserialize the model
                val model = deserializeModel(newFileName)

                // Send it to the repository
                if (waitForLoad) {
                    val fut = Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ? new UpsertModel(newModelName, model, true)
                    // Wait for response
                    Await.result(fut, timeout.duration)
                } else
                    Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new UpsertModel(newModelName, model, false)
            }
        }

        data
    })

    def deserializeModel(filename: String): BM = ???
}

/**
 * Destroys a model / removes it from memory
 */
class MLDestroyProcessor(resultName: String) extends BaseProcessor(resultName) {
    var modelName: String = _
    var destroyOnEOF: Boolean = _
    val toBeDestroyed = collection.mutable.Set.empty[String]

    override def initialize(config: JsObject) {
        modelName = (config \ "model_name").as[String]
        destroyOnEOF = (config \ "destroy_on_eof").asOpt[Boolean].getOrElse(true)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        val newModelName = utils.evaluateTuktuString(modelName, data.data.headOption.getOrElse(Map.empty))
        if (!destroyOnEOF)
            Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new DestroyModel(newModelName)
        else
            toBeDestroyed += newModelName

        data
    }) compose Enumeratee.onEOF(() => for (modelName <- toBeDestroyed) {
        // Send model repository the signal to clean up the model
        Akka.system.actorSelection("user/tuktu.ml.ModelRepository") ! new DestroyModel(modelName)
    })
}