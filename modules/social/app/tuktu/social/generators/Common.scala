package tuktu.social.generators

import play.api.libs.json._

object Common {
	def getFilters(config: JsValue) = {
	    // Get filters
	     val filters = (config \ "filters").as[JsObject]
	     val keywords = {
	         val keywordJson = (filters \ "keywords").asOpt[List[String]]
	         keywordJson match {
	             case Some(json) => json.toArray
	             case None => Array[String]()
	         }
	     }
	     val userids = {
	         val usersJson = (filters \ "users").asOpt[List[String]]
	         usersJson match {
	             case Some(json) => json.toArray
	             case None => Array[String]()
	         }
	     }
	     val geoJson = (filters \ "geo").asOpt[JsObject]
	     val geo = geoJson match {
	         case Some(json) => {
		         val p1 = (json \ "p1").as[JsObject]
		         val p2 = (json \ "p2").as[JsObject]
		         Array(
		                 Array(
		                         (p1 \ "long").as[String].toDouble,
		                         (p1 \ "lat").as[String].toDouble
		                 ),
		                 Array(
		                         (p2 \ "long").as[String].toDouble,
		                         (p2 \ "lat").as[String].toDouble
		                 )
		         )
	         }
	         case None => Array[Array[Double]]()
	     }
	     Map("keywords" -> keywords, "userids" -> userids, "geo" -> geo)
	}
}