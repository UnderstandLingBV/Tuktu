package controllers.modeller

import play.api.mvc._
import play.api.Routes
import routes.javascript.Application.saveConfig

object JavascriptRoute extends Controller {
    def javascriptRoutes: EssentialAction = Action { implicit request =>
        import routes.javascript._
        Ok(Routes.javascriptRouter("jsRoutes")(saveConfig)).as("text/javascript")
    }
}