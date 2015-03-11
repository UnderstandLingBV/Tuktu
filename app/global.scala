import java.io.File
import akka.actor.Actor
import akka.actor.Props
import play.api.Application
import play.api.GlobalSettings
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json
import com.fasterxml.jackson.core.JsonParseException

object Global extends GlobalSettings {
    
    def setCache() { 
        // Cache the meta-repository location and load generator and processor descriptors
        val metaLocation = Play.current.configuration.getString("tuktu.metarepo").getOrElse("meta")
        val genInfo = new File(metaLocation + "/generators").listFiles
        val procInfo = new File(metaLocation + "/processors").listFiles
        
        // Add all files to a map, indexed by name
        val generators = (for {
                file <- genInfo
                
                (parseable, json) = try {
                    // Open file and read contents into memory
                    val content = scala.io.Source.fromFile(file)("utf-8")
                    val js = Json.parse(content.getLines.mkString(""))
                    content.close
                    
                    (true, js)
                } catch {
                    case e: JsonParseException => {
                        System.err.println("Invalid JSON found in file " + file.getAbsoluteFile)
                        (false, null)
                    }
                    case e: Exception => {
                        e.printStackTrace
                        (false, null)
                    }
                }
                
                if (parseable)
        } yield {
            // Get name and add
            (json \ "name").as[String] -> json
        }).toMap
        
        // Same for processors
        val processors = (for {
            file <- procInfo
            
            (parseable, json) = try {
                // Open file and read contents into memory
                val content = scala.io.Source.fromFile(file)("utf-8")
                val js = Json.parse(content.getLines.mkString(""))
                content.close
                
                (true, js)
            } catch {
                case e: JsonParseException => {
                    System.err.println("Invalid JSON found in file " + file.getAbsoluteFile)
                    (false, null)
                }
                case e: Exception => {
                    e.printStackTrace
                    (false, null)
                }
            }
            
            if (parseable)
        } yield {
            // Get name and add
            (json \ "name").as[String] -> json
        }).toMap
        
        // Cache
        Cache.set("generators", generators)
        Cache.set("processors", processors)
    }

    /**
     * Load this on startup. The application is given as parameter
     */
    override def onStart(app: Application) {
        val metaActor = Akka.system.actorOf(Props(new Actor {
            def receive = {
                case "cache" ⇒  setCache
            }
        }))
        
        setCache
        // Load meta info, update every 5 minutes
        Akka.system.scheduler.schedule(
            5 minutes,
            5 minutes,
            metaActor,
            "cache")
    }
}
