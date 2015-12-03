package tuktu.ml.models

import akka.actor.ActorLogging
import akka.actor.Actor

// Helper classes for messages
case class GetModel(
    name: String
)
case class ExistsModel(
    name: String
)
case class UpsertModel(
    name: String,
    model: BaseModel,
    reply: Boolean
)
case class DestroyModel(
    name: String
)

/**
 * This actor serves as an in-memory repository of machine learning models.
 * It can serve out machine learner models that were persisted in memory, it can
 * store new ones in memory and it can destroy ML models.
 */
class ModelRepository() extends Actor with ActorLogging {
    val modelRepository = collection.mutable.Map[String, BaseModel]()

    def receive() = {
        case "init" => {
            // Initialize
        }
        case gm: GetModel => {
            // Check if model exists and return Some(model) or None to sender
            sender ! modelRepository.get(gm.name)
        }
        case em: ExistsModel => {
            // Check if a model with the given name was already initialized
            sender ! modelRepository.contains(em.name)
        }
        case um: UpsertModel => {
            // Insert or overwrite the model
            modelRepository += um.name -> um.model
            // See if we need to reply
            if (um.reply) sender ! "ok"
        }
        case dm: DestroyModel => {
            // Simply remove it from the repository
            modelRepository -= dm.name
        }
    }
}