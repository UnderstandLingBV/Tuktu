package controllers.modeller

import play.api._
import play.api.mvc._
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.json.JsValue

object Application extends Controller {
    def index = Action {
        // Get generators and processors
        
        val generators = Cache.getAs[Iterable[(String, Iterable[(String, JsValue)])]]("generators").getOrElse(List())
        val processors = Cache.getAs[Iterable[(String, Iterable[(String, JsValue)])]]("processors").getOrElse(List())

        Ok(views.html.modeller.index(generators, processors))
    }
}
