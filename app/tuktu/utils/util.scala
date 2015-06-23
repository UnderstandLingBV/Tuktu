package tuktu.utils

import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.mvc.AnyContent
import play.api.mvc.Request

object util {
    /**
     * Converts a flash from an HTTP request into a map of messages
     */
    def flashMessagesToMap(request: Request[AnyContent]) = {
        (
            request.flash.get("error") match {
                case Some(error) => Map(
                        "errors" -> error.split(";").toList
                )
                case _ => Map()
            }
        ) ++ (
            request.flash.get("success") match {
                case Some(success) => Map(
                        "success" -> success.split(";").toList
                )
                case _ => Map()
            }
        ) ++ (
            request.flash.get("info") match {
                case Some(info) => Map(
                        "info" -> info.split(";").toList
                )
                case _ => Map()
            }
        )
    }
}