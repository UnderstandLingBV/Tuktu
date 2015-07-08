package controllers

import play.api.mvc.Controller
import play.api.mvc.Action

object DFSWrapper extends Controller {
    def index() = Action {
        Ok(views.html.dfswrapper.dfsWrapper())
    }
}