package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.http.HeaderNames
import play.api.mvc.Request
import scala.concurrent.Future
import play.api.mvc.Result
import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller {
    // Adds the CORS header
    case class CorsAction[A](action: Action[A]) extends Action[A] {

        def apply(request: Request[A]): Future[Result] = {
            action(request).map(result => result.withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*",
                HeaderNames.ALLOW -> "*",
                HeaderNames.ACCESS_CONTROL_ALLOW_METHODS -> "POST, GET, PUT, DELETE, OPTIONS",
                HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS -> "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent"))
        }

        lazy val parser = action.parser
    }

    def options(path: String) = CorsAction {
        Action { request =>
            Ok.withHeaders(ACCESS_CONTROL_ALLOW_HEADERS -> Seq(AUTHORIZATION, CONTENT_TYPE, "Target-URL").mkString(","))
        }
    }
}