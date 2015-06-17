package globals

import play.api.GlobalSettings
import play.api.Application

object MLGlobal extends GlobalSettings {
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) {
        println("Booted global of ML")
    }
}