package tuktu.processors.cache

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsValue
import scala.concurrent.Future
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.iteratee.Enumerator
import akka.actor.Props
import tuktu.processors.meta.ParallelProcessorActor
import play.api.libs.concurrent.Akka
import akka.actor.ActorRef
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.DurationInt

/**
 * Requests something from cache, but if not present, executes an embedded processor to populate the cache
 */
class CachingProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var cacheKey = ""
    var resultField = ""
    var startProcessor = ""
    var actor: ActorRef = _

    override def initialize(config: JsObject) {
        // The key name of the
        cacheKey = (config \ "cache_key").as[String]
        // Get the field we are after as result
        resultField = (config \ "result_field").as[String]
        // Get the starting processor of our flow
        startProcessor = (config \ "start").as[String]

        // Get the processor flow
        val processors = (config \ "processors").as[List[JsObject]]
        val processorMap = (for (processor <- processors) yield {
            // Get all fields
            val processorId = (processor \ "id").as[String]
            val processorName = (processor \ "name").as[String]
            val processorConfig = (processor \ "config").as[JsObject]
            val resultName = (processor \ "result").as[String]
            val next = (processor \ "next").as[List[String]]

            // Create processor definition
            val procDef = new ProcessorDefinition(
                processorId,
                processorName,
                processorConfig,
                resultName,
                next)

            // Return map
            processorId -> procDef
        }).toMap

        // Build the processor pipeline for this generator
        val (idString, proc) = {
            val pipeline = controllers.Dispatcher.buildEnums(List(startProcessor), processorMap, "TuktuMonitor", None)
            (pipeline._1, pipeline._2.head)
        }

        // Set up the single actor that will execute this processor
        actor = Akka.system.actorOf(Props(classOf[ParallelProcessorActor], processor))
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Consult our cache first
            Cache.get(cacheKey) match {
                case Some(value) => {
                    // It was cached, include it!
                    datum + (resultName -> value)
                }
                case None => {
                    // The value was not cached yet, let's compute it
                    val result = Await.result((actor ? data).asInstanceOf[Future[DataPacket]], timeout.duration)
                    
                    // Terminate actor
                    actor ! new StopPacket

                    datum + (resultName -> result)
                }
            }
        })
    })
}