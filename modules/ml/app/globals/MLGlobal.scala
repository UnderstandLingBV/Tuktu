package globals

import play.api.GlobalSettings
import play.api.Application
import akka.actor.Props
import tuktu.ml.models.ModelRepository
import play.api.libs.concurrent.Akka
import play.api.Play.current

class MLGlobal extends GlobalSettings {
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) {
        // Set up the model repository
        val repoActor = Akka.system.actorOf(Props[ModelRepository], name = "tuktu.ml.ModelRepository")
        repoActor ! "init"
    }
}