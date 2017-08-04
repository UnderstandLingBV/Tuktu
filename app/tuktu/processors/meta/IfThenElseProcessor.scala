package tuktu.processors.meta

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import play.api.Logger
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.Parsing.PredicateParser
import tuktu.api.ProcessorDefinition
import tuktu.api.utils
import tuktu.api.utils._
import tuktu.api.AppInitPacket
import play.api.libs.concurrent.Akka
import play.api.Play.current
import tuktu.api.AppMonitorUUIDPacket

class IfThenElseProcessor(resultName: String) extends BaseProcessor(resultName) {
    var thenProcessor: Enumeratee[DataPacket, DataPacket] = _
    var elseProcessor: Enumeratee[DataPacket, DataPacket] = _
    val iteratee: Iteratee[DataPacket, List[Map[String, Any]]] = Iteratee.fold(List.empty[Map[String,Any]])((acc, next) => {
        acc ++ next.data
    })
    
    var constant: Option[String] = _
    var default: Option[Boolean] = _
    var expression: evaluateTuktuString.TuktuStringRoot = _
    var evaluatedAndParsedExpression: Option[Try[PredicateParser.BooleanNode]] = None
    var lastWarning: Long = 0
    var warningsSinceLast: Int = 0
    var evaluate: Boolean = _
    var name: String = _
    
    /**
     * Evaluates an expression
     */
    def evaluateExpression(datum: Map[String, Any]): Boolean = {
        try {
            if (constant.isDefined)
                evaluatedAndParsedExpression.get.get.evaluate(datum)
            else
                PredicateParser(expression.evaluate(datum), datum)
        } catch {
            case e: Throwable =>
                warningsSinceLast += 1
                // Only show at most one warning per 0.1s
                if (System.currentTimeMillis - 100 > lastWarning) {
                    Logger.warn("Could not evaluate " + warningsSinceLast + " Tuktu Predicate expressions since last warning of this type in this processor.\nThe last expression that failed was:\n" + expression + "\n" + "Defaulting to: " + default, e)
                    lastWarning = System.currentTimeMillis
                    warningsSinceLast = 0
                }
                default.get
        }
    }
    
    override def initialize(config: JsObject) {
        name = (config \ "name").asOpt[String].getOrElse("UNNAMED")
        /**
         * Parsing parameters
         */
        evaluate = (config \ "evaluate").asOpt[Boolean].getOrElse(true)
        constant = (config \ "constant").asOpt[String].flatMap {
            case _ if evaluate == false               => Some("global")
            case "global" | "'global'" | "\"global\"" => Some("global")
            case "local" | "'local'" | "\"local\""    => Some("local")
            case _                                    => None
        }
        expression = if (evaluate) evaluateTuktuString.prepare((config \ "expression").as[String])
            else {
                val expr = (config \ "expression").as[String]
                // If we don't need to evaluate it, we can prepare the predicate expression already
                evaluatedAndParsedExpression = Some(Try(PredicateParser.prepare(expr)))
                evaluateTuktuString.TuktuStringRoot(Seq(evaluateTuktuString.TuktuStringString(expr)))
            }

        default = (config \ "default") match {
            case b: JsBoolean => Some(b.value)
            case s: JsString  => Some(s.value.toLowerCase.replaceAll("[^a-z]", "").toBoolean)
            case _            => None
        }
        
        /**
         * Subflow parameters
         */
        thenProcessor = {
            val thenJson = (config \ "then_pipeline").as[JsObject]
            val configName = (thenJson \ "config").as[String]
            val start = (thenJson \ "start").as[String]
            
            // Get the content and build the pipeline
            val procs = (utils.loadConfig(configName).get \ "processors").as[List[JsObject]]
            val processorMap = (for (processor <- procs) yield {
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
            
            val thenPipeline = controllers.Dispatcher.buildEnums(List(start), processorMap, None, "If-then-else-processor - Then-branch - " + name, true)
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppInitPacket(
                    thenPipeline._1,
                    "If-then-else-processor - Then-branch - " + name,
                    1,
                    true,
                    None
            )
            thenPipeline._2.head compose Enumeratee.onEOF { () =>
                Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorUUIDPacket(thenPipeline._1, "done")
            }
        }
        
        elseProcessor = {
            val thenJson = (config \ "else_pipeline").as[JsObject]
            val configName = (thenJson \ "config").as[String]
            val start = (thenJson \ "start").as[String]
            
            // Get the content and build the pipeline
            val procs = (utils.loadConfig(configName).get \ "processors").as[List[JsObject]]
            val processorMap = (for (processor <- procs) yield {
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
            
            val elsePipeline = controllers.Dispatcher.buildEnums(List(start), processorMap, None, "If-then-else-processor - Else-branch - " + name, true)
            Akka.system.actorSelection("user/TuktuMonitor") ! new AppInitPacket(
                    elsePipeline._1,
                    "If-then-else-processor - Else-branch - " + name,
                    1,
                    true,
                    None
            )
            elsePipeline._2.head compose Enumeratee.onEOF { () =>
                Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorUUIDPacket(elsePipeline._1, "done")
            }
        }
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // If we need to evaluate a non-groovy expression, which isn't already prepared (because it's global or static), and is some form of constant, and the DP is not empty,
        // then use the first datum to prepare the predicate expression
        if (evaluate == true && evaluatedAndParsedExpression.isEmpty && constant.isDefined && data.nonEmpty)
            evaluatedAndParsedExpression = Some(Try(PredicateParser.prepare(expression.evaluate(data.data.head))))
            
        // Split dataset in everything for then and else
        val (thens, elses) = data.data.partition(datum => evaluateExpression(datum))
        
        // If it's only locally constant, reset it to None so that it will be set by the next DP anew
        if (constant == Some("local"))
            evaluatedAndParsedExpression = None
        
        // Send each off to their own processor pipelines
        val thensResults = Enumerator(DataPacket(thens)).run(thenProcessor &>> iteratee)
        val elsesResults = Enumerator(DataPacket(elses)).run(elseProcessor &>> iteratee)
        
        // Combine data and send on
        Future.sequence(List(thensResults, elsesResults)).map {
            case result =>
                // Put it all together in a datapacket and forward
                new DataPacket(result.flatten)
        }
    }) compose Enumeratee.onEOF{() =>
        if (thenProcessor != null) Enumerator.eof.run(thenProcessor &>> Iteratee.ignore)
        if (elseProcessor != null) Enumerator.eof.run(elseProcessor &>> Iteratee.ignore)
    }
}