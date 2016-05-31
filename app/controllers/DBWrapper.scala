package controllers

import play.api.mvc.Controller
import play.api.mvc.Action

object DBWrapper extends Controller {
    def index() = Action {
        Ok(views.html.dbwrapper.dbWrapper())
    }
}