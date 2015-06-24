package tuktu.ml.models

import akka.actor.ActorLogging
import akka.actor.Actor

// Helper classes for messages
case class GetModel (
        name: String
)
case class UpsertModel (
        name: String,
        model: BaseModel
)
case class DestroyModel (
        name: String
)
case class SerializeModel (
        name: String,
        fileName: String
)
case class DeserializeModel (
        name: String,
        fileName: String
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
            // Check if the model exists, otherwise initialize it
            modelRepository contains gm.name match {
                case true => sender ! Some(modelRepository(gm.name))
                case false => sender ! None
            }
        }
        case um: UpsertModel => {
            // Insert or overwrite the model
            modelRepository += um.name -> um.model
        }
        case dm: DestroyModel => {
            // Simply remove it from the repository
            modelRepository -= dm.name
        }
        case sm: SerializeModel => {
            // Get the model and write it out
            modelRepository(sm.name).serialize(sm.fileName)
        }
        case dm: DeserializeModel => {
            // @TODO: Load model back into repository
            
        }
    }
}