package controllers.modeller

import java.nio.file.{ Files, Paths, StandardOpenOption }
import play.api.mvc._
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.json.{ Json, JsArray, JsObject, JsValue }

object Application extends Controller {
    def index(file: String) = Action { implicit request =>
        // Get generators and processors
        val generators = Cache.getAs[Iterable[(String, Iterable[(String, JsValue)])]]("generators").getOrElse(Nil)
        val processors = Cache.getAs[Iterable[(String, Iterable[(String, JsValue)])]]("processors").getOrElse(Nil)

        // Get and normalize path
        val path = Paths.get("configs", file).toAbsolutePath.normalize

        (try {
            // Check if it starts with the configs folder (symlinks and hardlinks are not handled)
            if (path.startsWith(Paths.get("configs").toAbsolutePath.normalize))
                // Try to parse
                Json.parse(Files.readAllBytes(path)).asOpt[JsObject]
            else
                None
        } catch {
            case _: Throwable => None
        }) match {
            case Some(json) => Ok(views.html.modeller.index(generators, processors, Json.stringify(json), file))
            case None       => BadRequest
        }
    }

    def saveConfig(file: String) = Action { implicit request =>
        val path = Paths.get("configs", file).toAbsolutePath.normalize
        if (path.startsWith(Paths.get("configs").toAbsolutePath.normalize)) {
            request.body.asText match {
                case None => BadRequest
                case Some(str) => {
                    try {
                        Files.write(path, str.getBytes("utf-8"), StandardOpenOption.TRUNCATE_EXISTING)
                        Ok
                    } catch {
                        case e: Throwable => {
                            e.printStackTrace
                            InternalServerError
                        }
                    }
                }
            }
        } else {
            BadRequest
        }
    }
}
