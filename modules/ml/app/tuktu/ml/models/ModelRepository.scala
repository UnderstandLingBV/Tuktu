package tuktu.ml.models

import akka.actor.ActorLogging
import akka.actor.Actor

// Helper classes for messages
case class AddModel[BM <: BaseModel] (
        model: BM
)
case class GetModel[BM <: BaseModel] (
        name: String,
        modelType: BM
)
case class DestroyModel (
        name: String
)

/**
 * This actor serves as an in-memory repository of machine learning models.
 * It can serve out machine learner models that were persisted in memory, it can
 * store new ones in memory and it can destroy ML models.
 */
class ModelRepository() extends Actor with ActorLogging {
    def receive() = {
        case _ => {}
    }
}