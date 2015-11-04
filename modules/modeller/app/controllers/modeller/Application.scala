package controllers.modeller

import java.nio.file.{ Files, Paths, StandardOpenOption }
import play.api.mvc._
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.json.{ Json, JsObject, JsValue }
import play.api.Logger

object Application extends Controller {
    def index(file: String) = Action { implicit request =>
        // Get generator and processor definitions from Cache
        val generators = Cache.getOrElse[Iterable[(String, Iterable[(String, JsValue)])]]("generators")(Nil)
        val processors = Cache.getOrElse[Iterable[(String, Iterable[(String, JsValue)])]]("processors")(Nil)

        // Get configs repository and normalize absolute file path
        val configsRepo = Cache.getOrElse[String]("configRepo")("configs")
        val path = Paths.get(configsRepo, file).toAbsolutePath.normalize

        (try {
            // Check if it starts with the configs folder (symlinks and hardlinks are not handled)
            if (path.startsWith(Paths.get(configsRepo).toAbsolutePath.normalize))
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
        // Get configs repository and normalize absolute file path
        val configsRepo = Cache.getOrElse[String]("configRepo")("configs")
        val path = Paths.get(configsRepo, file).toAbsolutePath.normalize
        // Check if it starts with the configs folder (symlinks and hardlinks are not handled)
        if (path.startsWith(Paths.get(configsRepo).toAbsolutePath.normalize)) {
            request.body.asText match {
                case None =>
                    BadRequest
                case Some(str) => {
                    try {
                        Files.write(path, str.getBytes("utf-8"), StandardOpenOption.TRUNCATE_EXISTING)
                        Ok
                    } catch {
                        case e: Throwable => {
                            Logger.error("Can't write to config",e)                            
                            InternalServerError
                        }
                    }
                }
            }
        } else {
            BadRequest
        }
    }

    /**
     * End point for web socket event listener 
     */
    def webSocket = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
        models.modeller.ModellerEventListener.props(out)
    }
}