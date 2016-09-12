package tuktu.processors.meta

import java.lang.reflect.Method
import java.util.concurrent.TimeoutException
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee._
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import tuktu.api._
import akka.routing.Broadcast
import java.nio.file.{ Files, Paths }
import java.util.concurrent.atomic.{ AtomicInteger, AtomicBoolean }

/**
 * Invokes a new generator for every DataPacket received
 * The first Datum, if available, is used to populate the config
 */
class GeneratorConfigProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var name: String = _
    var replacements: Map[String, String] = _

    override def initialize(config: JsObject) {
        // Get the name of the config file
        name = (config \ "name").as[String]

        // Get meta replacements
        replacements = (config \ "replacements").asOpt[List[Map[String, String]]].getOrElse(Nil).map(map => map("source") -> map("target")).toMap
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        val datum = data.data.headOption.getOrElse(Map())

        // Resolve name with potential variables
        val nextName = utils.evaluateTuktuString(name, datum)

        // Open the config file to get contents, and evaluate as TuktuConfig
        val configFile = scala.io.Source.fromFile(Cache.getAs[String]("configRepo").getOrElse("configs") +
            "/" + nextName + ".json", "utf-8")
        val conf = utils.evaluateTuktuConfig(Json.parse(configFile.mkString).as[JsObject],
            replacements.map(kv => utils.evaluateTuktuString(kv._1, datum) -> utils.evaluateTuktuString(kv._2, datum)))
        configFile.close

        // Invoke the new generator with custom config
        Akka.system.actorSelection("user/TuktuDispatcher") !
            new DispatchRequest(nextName, Some(conf), false, false, false, None)

        // We can still continue with out data
        data
    })
}

/**
 * This class is used to always have an actor present when data is to be streamed in sync
 */
class SyncStreamForwarder() extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var remoteGenerator: ActorRef = null
    var sync: Boolean = false

    def receive() = {
        case setup: (ActorRef, Boolean) => {
            remoteGenerator = setup._1
            sync = setup._2
            sender ! true
        }
        case dp: DataPacket => sync match {
            case false => remoteGenerator ! dp
            case true  => sender ! Await.result((remoteGenerator ? dp).mapTo[DataPacket], timeout.duration)
        }
        case sp: StopPacket => {
            remoteGenerator ! Broadcast(new StopPacket)
        }
    }
}

/**
 * Invokes a new generator for every DataPacket received
 * The first Datum, if available, is used to populate the config
 */
class GeneratorStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    val forwarder = Akka.system.actorOf(Props[SyncStreamForwarder], name = "SyncStreamForwarder_" + java.util.UUID.randomUUID.toString)

    var processorConfig: JsObject = new JsObject(Seq())

    var sync: Boolean = false

    override def initialize(config: JsObject) {
        processorConfig = config
        sync = (config \ "sync").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => {
        // Populate and dispatch remote generator with contents of first Datum
        val datum = data.data.headOption.getOrElse(Map())

        // Populate config
        val config = utils.evaluateTuktuConfig(processorConfig, datum)

        // Get the name of the config file
        val nextName = (config \ "name").as[String]
        // Node to execute on
        val nodes = (config \ "node").asOpt[String]
        // Get the processors to send data into
        val next = (config \ "next").as[List[String]]
        // Get the actual config, being a list of processors
        val processors = (config \ "processors").as[List[JsObject]]

        // Manipulate config and set up the remote actor
        val customConfig = Json.obj(
            "generators" -> List((Json.obj(
                "name" -> {
                    sync match {
                        case true  => "tuktu.generators.SyncStreamGenerator"
                        case false => "tuktu.generators.AsyncStreamGenerator"
                    }
                },
                "result" -> "",
                "config" -> Json.obj(),
                "next" -> next) ++
                (nodes match {
                    case Some(n) => Json.obj("nodes" -> Json.arr(Json.obj(
                        "type" -> "SingleNode",
                        "nodes" -> n,
                        "instances" -> 1)))
                    case None => Json.obj()
                }))),
            "processors" -> processors)

        try {
            // Send a message to our Dispatcher to create the (remote) actor and return us the ActorRef
            val fut = Akka.system.actorSelection("user/TuktuDispatcher") ?
                new DispatchRequest(nextName, Some(customConfig), false, true, sync, Some(forwarder))
            val ar = Await.result(fut.mapTo[ActorRef], timeout.duration)
            // Tell the sync stream forwarder about the ActorRef
            val successFut = forwarder ? (ar, sync)
            Await.result(successFut.mapTo[Boolean], timeout.duration)
        } catch {
            case e: TimeoutException     => {}
            case e: NullPointerException => {}
        }

        // Send the result to the generator
        if (sync) {
            // Get the result from the generator
            val dataFut = (forwarder ? data).asInstanceOf[Future[DataPacket]]
            dataFut.map {
                case result: DataPacket => {
                    forwarder ! new StopPacket
                    result
                }
            }
        } else {
            forwarder ! data
            forwarder ! new StopPacket
            Future { data }
        }
    }) compose Enumeratee.onEOF(() => {
        forwarder ! PoisonPill
    })
}

/**
 * Invokes a new generator for every DataPacket received
 * The first Datum, if available, is used to populate the config
 */
class GeneratorConfigStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var name: String = _
    var nodes: JsObject = _
    var next: List[String] = _
    var flowPresent = true
    var flowField: String = _
    var sendWhole = false
    var replacements: Map[String, String] = _
    var keepAlive: Boolean = _
    var remoteGeneratorFut: Future[Any] = _
    val remaining = new AtomicInteger(0)
    val done = new AtomicBoolean(false)

    override def initialize(config: JsObject) {
        // Get the name of the config file
        name = (config \ "name").as[String]
        // Node to execute on
        val instances = (config \ "instances").asOpt[Int].getOrElse(1)
        nodes = (config \ "nodes").asOpt[String] match {
            case Some(n) => Json.obj("nodes" -> Json.obj(
                "type" -> "SingleNode",
                "node" -> n,
                "instances" -> instances))
            case None => Json.obj()
        }
        // Get the processors to send data into
        next = (config \ "next").as[List[String]]

        // Check if the flow of processors if given or in an external JSON
        flowPresent = (config \ "flow_given").asOpt[Boolean].getOrElse(true)

        // The field of the JSON to load
        flowField = (config \ "flow_field").as[String]

        // Send the whole datapacket or in pieces
        sendWhole = (config \ "send_whole").asOpt[Boolean].getOrElse(false)

        // Get meta replacements
        replacements = (config \ "replacements").asOpt[List[Map[String, String]]].getOrElse(Nil).map(map => map("source") -> map("target")).toMap

        // Should we keep the async flow alive or create a new one each DP?
        keepAlive = (config \ "keep_alive").asOpt[Boolean].getOrElse(false)
        if (keepAlive) {
            // Set up the remote generator, just once
            val processors = {
                val configFile = scala.io.Source.fromFile(Cache.getAs[String]("configRepo").getOrElse("configs") +
                    "/" + flowField + ".json", "utf-8")
                val cfg = Json.parse(configFile.mkString).as[JsObject]
                configFile.close
                (cfg \ "processors").as[List[JsObject]]
            }
            // Manipulate config and set up the remote actor
            val customConfig = Json.obj(
                "generators" -> List((Json.obj(
                    "name" -> "tuktu.generators.AsyncStreamGenerator",
                    "result" -> "",
                    "config" -> Json.obj(),
                    "next" -> next) ++ nodes)),
                "processors" -> processors)

            // Send a message to our Dispatcher to create the (remote) actor and return us the ActorRef
            remoteGeneratorFut = Akka.system.actorSelection("user/TuktuDispatcher") ? new DispatchRequest(name, Some(customConfig), false, true, false, None)
        }
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        if (!keepAlive) {
            if (!sendWhole) {
                for (datum <- data)
                    forwardData(List(datum))
            } else forwardData(data.data)
        } else {
            // Callback order on Futures is not guaranteed, so we need to keep track of number of remaining packages to be sent to actorRef
            // Increment count, and once the data has been sent, decrement the count; if it's 0 and we have reached EOF, broadcast Stop
            remaining.incrementAndGet
            remoteGeneratorFut onSuccess { case actorRef: ActorRef =>
                actorRef ! data
                if (remaining.decrementAndGet == 0 && done.get == true)
                    actorRef ! Broadcast(new StopPacket)
            }
        }

        data
    }) compose Enumeratee.onEOF(() => {
        if (keepAlive)
            remoteGeneratorFut onSuccess { case actorRef: ActorRef =>
                // We have reached EOF, we are done; if no packages are remaining to be sent, broadcast Stop right away,
                // otherwise it will be done right after the last package has been sent
                done.set(true)
                if (remaining.get == 0)
                    actorRef ! Broadcast(new StopPacket)
            }
    })

    def forwardData(data: List[Map[String, Any]]) {
        val datum = data.headOption.getOrElse(Map())
        val path = utils.evaluateTuktuString(flowField, datum)
        val processors = {
            val configFile = scala.io.Source.fromFile(Cache.getAs[String]("configRepo").getOrElse("configs") +
                "/" + path + ".json", "utf-8")
            val cfg = utils.evaluateTuktuConfig(Json.parse(configFile.mkString).as[JsObject],
                replacements.map(kv => utils.evaluateTuktuString(kv._1, datum) -> utils.evaluateTuktuString(kv._2, datum)))
            configFile.close
            (cfg \ "processors").as[List[JsObject]]
        }
        // Manipulate config and set up the remote actor
        val customConfig = Json.obj(
            "generators" -> List((Json.obj(
                "name" -> "tuktu.generators.AsyncStreamGenerator",
                "result" -> "",
                "config" -> Json.obj(),
                "next" -> next) ++ nodes)),
            "processors" -> processors)

        // Send a message to our Dispatcher to create the (remote) actor and return us the actorref
        val fut = Akka.system.actorSelection("user/TuktuDispatcher") ? new DispatchRequest(name, Some(customConfig), false, true, false, None)
        // Make sure we get actorref set before sending data
        fut onSuccess {
            case generatorActor: ActorRef => {
                //send the data forward
                generatorActor ! DataPacket(data)
                // Directly send stop packet
                generatorActor ! Broadcast(new StopPacket)
            }
        }
    }
}

/**
 * Actor that deals with parallel processing
 */
class ParallelProcessorActor(processor: Enumeratee[DataPacket, DataPacket]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore

    /**
     * We must somehow keep track of the sending actor of each data packet. This state is kept within this helper class that
     * is to be instantiated for each data packet
     */
    class senderReturningProcessor(senderActor: ActorRef, dp: DataPacket) {
        // Create enumeratee that will send back
        val sendBackEnum: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
            senderActor ! dp
            dp
        })

        def runProcessor() = Enumerator(dp).andThen(Enumerator.eof) |>> (processor compose sendBackEnum compose utils.logEnumeratee("")) &>> sinkIteratee
    }

    def receive() = {
        case sp: StopPacket => {
            self ! PoisonPill
        }
        case dp: DataPacket => {
            // Push to all async processors
            channel.push(dp)

            // Send through our enumeratee
            val p = new senderReturningProcessor(sender, dp)
            p.runProcessor()
        }
    }
}

/**
 * Executes a number of processor-flows in parallel
 */
class ParallelProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var actors: List[ActorRef] = null
    var merger: Method = null
    var mergerClass: Any = null
    var includeOriginal: Boolean = _
    var sendOriginal: Boolean = _

    override def initialize(config: JsObject) {
        // Process config
        val pipelines = (config \ "processors").as[List[JsObject]]

        // Should we merge the results into the original DataPacket
        includeOriginal = (config \ "include_original").asOpt[Boolean].getOrElse(false)
        sendOriginal = (config \ "send_original").asOpt[Boolean].getOrElse(true)

        // Set up the merger
        val mergerProcClazz = Class.forName((config \ "merger").as[String])
        mergerClass = mergerProcClazz.getConstructor().newInstance()
        merger = mergerProcClazz.getMethods.filter(m => m.getName == "merge").head

        // For each pipeline, build the enumeratee
        actors = for (pipeline <- pipelines) yield {
            val start = (pipeline \ "start").as[String]
            val procs = (pipeline \ "pipeline").as[List[JsObject]]
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

            // Build the processor pipeline for this generator
            val (idString, processor) = {
                val pipeline = controllers.Dispatcher.buildEnums(List(start), processorMap, None, "Parallel Processor - Unknown", true)
                (pipeline._1, pipeline._2.head)
            }
            // Set up the actor that will execute this processor
            Akka.system.actorOf(Props(classOf[ParallelProcessorActor], processor))
        }
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Send data to actors
        val futs = for (actor <- actors) yield (actor ? {
            if (sendOriginal) data else DataPacket(List())
        }).asInstanceOf[Future[DataPacket]]

        // Get the results
        val results = Await.result(Future.sequence(futs), timeout.duration)

        // Apply the merger
        if (includeOriginal)
            merger.invoke(mergerClass, data :: results).asInstanceOf[DataPacket]
        else
            merger.invoke(mergerClass, results).asInstanceOf[DataPacket]
    })

}

/**
 * Executes a number of processor-flows in parallel
 */
class ParallelConfigProcessor(resultName: String) extends BaseProcessor(resultName) {
    var timeout: Timeout = _
    var pipelines: List[JsObject] = _
    var merger: Method = _
    var mergerClass: Any = _
    var include_original: Boolean = _
    var send_whole: Boolean = _
    var replacements: Map[String, String] = _
    var sendOriginal: Boolean = _

    override def initialize(config: JsObject) {
        // Set up the merger
        val mergerProcClazz = Class.forName((config \ "merger").as[String])
        mergerClass = mergerProcClazz.getConstructor().newInstance()
        merger = mergerProcClazz.getMethods.find(m => m.getName == "merge").get

        // Should we merge the results into the original DataPacket
        include_original = (config \ "include_original").asOpt[Boolean].getOrElse(false)
        sendOriginal = (config \ "send_original").asOpt[Boolean].getOrElse(true)

        // Send the whole datapacket or in pieces
        send_whole = (config \ "send_whole").asOpt[Boolean].getOrElse(true)

        // Will set timeout, if it is specified, or default to this nodes timeout
        timeout = (config \ "timeout").asOpt[Int] match {
            case None    => Timeout(Cache.getAs[Int]("timeout").getOrElse(30) seconds)
            case Some(t) => Timeout(t seconds)
        }

        // Process config
        pipelines = (config \ "pipelines").as[List[JsObject]]

        // Get meta replacements
        replacements = (config \ "replacements").asOpt[List[Map[String, String]]].getOrElse(Nil).map(map => map("source") -> map("target")).toMap
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        if (send_whole) {
            // Process and merge DataPacket as a whole
            processData(data)
        } else {
            // Process and merge each datum individually, and concatenate the results into a new DataPacket
            val futs = for (datum <- data.data) yield Future(processData(DataPacket(List(datum))))
            val results = Await.result(Future.sequence(futs), timeout.duration)
            DataPacket(results.foldLeft[List[Map[String, Any]]](Nil)((x, y) => x ++ y.data))
        }
    })

    def processData(data: DataPacket): DataPacket = {
        // Get first datum to populate configs
        val datum = data.data.headOption.getOrElse(Map.empty)

        // For each pipeline and starting processor, build the Enumeratee and run our data through it
        val futs = (for (pipeline <- pipelines) yield Future {
            // Get and evaluate config file
            val path = utils.evaluateTuktuString((pipeline \ "config_path").as[String], datum)
            val localReplacements = (pipeline \ "replacements").asOpt[List[Map[String, String]]].getOrElse(Nil).map(map => map("source") -> map("target")).toMap
            val processorMap = {
                val configContent = Files.readAllBytes(Paths.get(Cache.getAs[String]("configRepo").getOrElse("configs"), path + ".json"))
                val cfg = utils.evaluateTuktuConfig(Json.parse(configContent).as[JsObject],
                    (replacements ++ localReplacements).map(kv => utils.evaluateTuktuString(kv._1, datum) -> utils.evaluateTuktuString(kv._2, datum)))
                controllers.Dispatcher.buildProcessorMap((cfg \ "processors").as[List[JsObject]])
            }

            // At what processor ID to start
            val start = (pipeline \ "start").as[List[String]]

            // Build the processor pipeline for this generator
            val (idString, enumeratees, subflows) = controllers.Dispatcher.buildEnums(start, processorMap, None, "Parallel Config Processor - Unknown", true)

            // Run our data through each Enumeratee and return the result chunk
            for (enumeratee <- enumeratees) yield {
                val inclMonitor = Enumeratee.mapM((data: DataPacket) => Future {
                    Akka.system.actorSelection("user/TuktuMonitor") ! new AppInitPacket(idString, "Parallel Config Processor - Unknown", 1, true)
                    data
                }) compose enumeratee compose Enumeratee.onEOF(() =>
                    Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorUUIDPacket(idString, "done")
                )
                Enumerator({
                    if (sendOriginal) data else DataPacket(List())
                }).through(inclMonitor).run(Iteratee.getChunks)
            }
        } flatMap (t => Future.sequence(t))) // Flatten Future[List[Future[T]]] => Future[List[T]]

        // Await the futures and flatten the results
        val results = Await.result(Future.sequence(futs), timeout.duration).flatten.flatten

        // Apply the merger
        if (include_original)
            merger.invoke(mergerClass, data :: results).asInstanceOf[DataPacket]
        else
            merger.invoke(mergerClass, results).asInstanceOf[DataPacket]
    }
}