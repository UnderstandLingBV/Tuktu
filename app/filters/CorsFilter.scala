package filters

import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.http.HeaderNames

object CorsFilter extends Filter {
    def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
        nextFilter(requestHeader).map { result =>
            result.withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*",
                HeaderNames.ALLOW -> "*",
                HeaderNames.ACCESS_CONTROL_ALLOW_METHODS -> "POST, GET, PUT, DELETE, OPTIONS",
                HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS -> "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent")
        }
    }
}