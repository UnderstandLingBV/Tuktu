package tuktu.social.generators

import play.api.libs.json._

object Common {
    def getFilters(config: JsValue): (Array[String], Array[String], Array[Array[Double]]) = {
        // Get filters
        val keywords = (config \ "filters" \ "keywords").asOpt[Array[String]].getOrElse(Array.empty)
        val userids = (config \ "filters" \ "users").asOpt[Array[String]].getOrElse(Array.empty)
        val geo = (config \ "filters" \ "geo").asOpt[JsObject].map { json =>
            val p1 = (json \ "p1").as[JsObject]
            val p2 = (json \ "p2").as[JsObject]
            Array(
                Array(
                    (p1 \ "long").as[String].toDouble,
                    (p1 \ "lat").as[String].toDouble),
                Array(
                    (p2 \ "long").as[String].toDouble,
                    (p2 \ "lat").as[String].toDouble))
        }.getOrElse(Array.empty)

        (keywords, userids, geo)
    }
}