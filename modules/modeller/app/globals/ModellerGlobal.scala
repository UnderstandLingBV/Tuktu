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
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import tuktu.api.TuktuGlobal

class ModellerGlobal() extends TuktuGlobal() {
    def getDescriptors(files: List[Path]): Map[String, JsValue] = {
        (for {
            file <- files

            (parseable, json) = try {
                // Read file and try to parse as json
                val js = Json.parse(Files.readAllBytes(file))

                (true, js)
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
            val map = Files.list(dir).collect(collector)

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
