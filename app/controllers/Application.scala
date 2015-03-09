package controllers

import play.api._
import play.api.mvc._
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.json.JsValue

object Application extends Controller {
    def index = Action {
        // Get generators and processors
        val generators = Cache.getAs[Map[String, JsValue]]("generators").getOrElse(Map()).map(elem => (elem._1, (elem._2 \ "class").asOpt[String].getOrElse("")))
        val processors = Cache.getAs[Map[String, JsValue]]("processors").getOrElse(Map()).map(elem => (elem._1, (elem._2 \ "class").asOpt[String].getOrElse("")))
        
        Ok(views.html.index(generators, processors))
    }
}
