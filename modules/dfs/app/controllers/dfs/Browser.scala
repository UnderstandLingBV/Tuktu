package controllers.dfs

import play.api.mvc.Action
import play.api.mvc.Controller

object Browser  extends Controller {
    def index = Action {
        Ok(views.html.dfs.browser())
    }
}