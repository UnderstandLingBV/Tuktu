package controllers

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.Play

object Modeller extends Controller {
    
    // Just shows the modeller embedded in Tuktu
    def modeller() = Action {
        // Get the location of the modeller
        val url = Play.current.configuration.getString("tuktu.modeller.url").getOrElse("http://localhost:9001")
        
        Ok(views.html.modeller.modeller(url))
    }
}