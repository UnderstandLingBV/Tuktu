package globals

import java.io.File
import java.nio.file.{ Path, Paths, Files }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.collection.JavaConversions.{ seqAsJavaList, asScalaBuffer }

import com.fasterxml.jackson.core.JsonParseException

import akka.actor.Actor
import akka.actor.Props
import play.api.Application
import play.api.GlobalSettings
import play.api.Logger
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.json.{ Json, JsArray, JsObject, JsValue }
import tuktu.api.TuktuGlobal

class ModellerGlobal() extends TuktuGlobal() {

    /**
     * Checks if a parameter contains all necessary fields
     */
    private def checkParameter(param: JsValue): Boolean = {
        try {
            val map = param.as[JsObject].value
            map.contains("type") && map.contains("name") && map.contains("required") && {
                // Check recursively
                map.get("parameters") match {
                    case None    => true
                    case Some(p) => p.as[Seq[JsValue]].forall(checkParameter(_))
                }
            }
        } catch {
            case _: Throwable => false
        }
    }

    def getDescriptors(files: List[Path]): Map[String, JsValue] = {
        (for {
            file <- files

            (parseable, json) = try {
                // Read file and try to parse as json
                val js = Json.parse(Files.readAllBytes(file))

                // Check if format is correct
                val parseable = {
                    val map = js.as[JsObject].value
                    if (!map.contains("name") || !map.contains("class")) {
                        Logger.error("Incorrect JSON found, keys 'name' and/or 'class' are missing in meta config file: " + file.toAbsolutePath.normalize)
                        false
                    } else
                        true
                } && {
                    val parameters = js.as[JsObject].value.get("parameters").getOrElse(new JsArray()).as[Seq[JsValue]]
                    if (!parameters.forall(checkParameter(_))) {
                        Logger.error("Incorrect JSON found, at least one parameter is missing 'type', 'name' and/or 'required' in meta config file: " + file.toAbsolutePath.normalize)
                        false
                    } else
                        true
                }

                (parseable, js)
            } catch {
                case e: JsonParseException => {
                    Logger.error("Invalid JSON found in meta config file: " + file.toAbsolutePath.normalize, e)
                    (false, null)
                }
                case e: Throwable => {
                    Logger.error("Other Exception while trying to read and parse meta config " + file.toAbsolutePath.normalize, e)
                    (false, null)
                }
            }

            if (parseable)
        } yield {
            // Get name and add
            (json \ "name").as[String] -> json
        }).toMap
    }

    def buildRecursiveMap(nameSoFar: List[Path], dir: Path): Map[String, Map[String, JsValue]] = {
        // Get name of this directory
        val dirName = nameSoFar ++ List(dir.getFileName)

        // Rudimentary check to see if meta directory is available
        if (Files.isDirectory(dir)) {
            // Define collector and partition files and directories
            val collector = java.util.stream.Collectors.groupingBy[Path, Boolean](
                new java.util.function.Function[Path, Boolean] {
                    def apply(path: Path): Boolean = Files.isDirectory(path)
                })
            val stream = Files.list(dir)
            val map = stream.collect(collector)
            stream.close

            // Get configs
            val configs = map.getOrDefault(false, Nil).toList
            // Get subfolders
            val subfolders = map.getOrDefault(true, Nil).toList

            // Add all files, recurse over dirs
            Map(dirName.mkString(".") -> getDescriptors(configs)) ++ subfolders.flatMap(dir => buildRecursiveMap(dirName, dir))
        } else {
            Map.empty
        }
    }

    def setCache() {
        // Cache the meta-repository location and load generator and processor descriptors
        val metaLocation = Play.current.configuration.getString("tuktu.metarepo").getOrElse("meta")

        if (!Files.isDirectory(Paths.get(metaLocation)))
            Logger.error("Meta configs directory not available!")

        val genDir = Paths.get(metaLocation, "generators")
        val procDir = Paths.get(metaLocation, "processors")

        // Start with generators and processors root folder
        val generators = buildRecursiveMap(Nil, genDir).mapValues(_.toList.sortBy(_._1.toLowerCase)).toList.sortBy(_._1.toLowerCase)
        val processors = buildRecursiveMap(Nil, procDir).mapValues(_.toList.sortBy(_._1.toLowerCase)).toList.sortBy(_._1.toLowerCase)

        // Cache
        Cache.set("generators", generators)
        Cache.set("processors", processors)
    }

    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) {
        setCache
        // Load meta info, update every 5 minutes
        Akka.system.scheduler.schedule(5 minutes, 5 minutes) {
            setCache
        }
    }
}
