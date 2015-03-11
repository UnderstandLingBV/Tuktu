package controllers

import play.api._
import play.api.mvc._
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.json.JsValue

object Application extends Controller {
    def index = Action {
        // Get generators and processors
        val generators = Cache.getAs[Map[String, JsValue]]("generators").getOrElse(Map()).toList.sortBy(_._1)
        val processors = Cache.getAs[Map[String, JsValue]]("processors").getOrElse(Map()).toList.sortBy(_._1)
        
        Ok(views.html.index(generators, processors))
    }
}
