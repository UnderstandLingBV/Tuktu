package globals

import tuktu.api.TuktuGlobal
import play.api.Application
import play.api.Play
import java.io.File
import tuktu.api.DispatchRequest
import play.api.libs.concurrent.Akka
import play.api.Play.current

/**
 * Loops through the web analytics configs and boots up instances of the flows present there
 */
class WebGlobal() extends TuktuGlobal() {
    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) = {
        // Get the web analytics configs
        val webRepo = Play.current.configuration.getString("tuktu.webrepo")
            .getOrElse("configs/analytics")
        val hostFolders = new File(webRepo).listFiles
        // These should all be folders that in turn contain a Tuktu.js file in them
        hostFolders.foreach(fldr => {
            val tuktuJsName = fldr.getAbsolutePath + "/Tuktu.json"
            // Sanity checks
            if (fldr.isDirectory) {
                // See if the Tuktu.js file is there
                try {
                    val tuktuJsFile = new File(tuktuJsName)
                    if (tuktuJsFile.exists) {
                        // Boot up the generator
                        Akka.system.actorSelection("user/TuktuDispatcher") !
                            new DispatchRequest(
                                    webRepo.drop(Play.current.configuration.getString("tuktu.configrepo").getOrElse("configs").size) + "/" + fldr.getName + "/Tuktu",
                                    None, false, false, true, None)
                    }
                } catch {
                    case e: Exception => {}
                }
            }
        })
    }
}