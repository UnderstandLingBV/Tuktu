package controllers.web

import java.net.URL
import java.nio.file._
import play.api.Play
import play.api.mvc._

object Application extends Controller {
    /**
     * Loads a JavaScript analytics script depending on referrer
     */
    def TuktuJs = Action { implicit request =>
        request.headers.get("referer") match {
            case None => BadRequest("// No referrer found in HTTP headers.")
            case Some(referrer) => {
                Play.current.configuration.getString("tuktu.webrepo") match {
                    case None => BadRequest("// No repository for JavaScripts and Tuktu flows set in Tuktu configuration.")
                    case Some(webRepo) => {
                        // Convert referrer to URL to get its host
                        val url = new URL(referrer)

                        // Check if host has folder and JavaScript file in web repo
                        if (!Files.exists(Paths.get(webRepo, url.getHost)))
                            BadRequest("// The referrer has no entry in the repository yet.")
                        else if (!Files.exists(Paths.get(webRepo, url.getHost, "Tuktu.js")))
                            BadRequest("// The referrer has no analytics script defined yet.")
                        else
                            Ok(Files.readAllBytes(Paths.get(webRepo, url.getHost, "Tuktu.js"))).as("text/javascript")
                    }
                }
            }
        }
    }
}