package tuktu.processors

import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.util.concurrent.TimeoutException
import java.util.regex.Matcher
import java.util.regex.Pattern
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import au.com.bytecode.opencsv.CSVWriter
import groovy.util.Eval
import play.api.Play.current
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import akka.actor.ActorLogging
import akka.actor.Actor
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Concurrent
import scala.concurrent.Future
import tuktu.api.StopPacket
import tuktu.api.DataMerger
import java.lang.reflect.Method

object util {
	def fieldParser(input: Map[String, Any], path: List[String], defaultValue: Option[Any]): Any = path match {
	    case someKey::List() => input(someKey)
	    case someKey::trailPath => {
	        // Get the remainder
	        if (input.contains(someKey)) {
	            fieldParser(input(someKey).asInstanceOf[Map[String, Any]], trailPath, defaultValue)
	        } else {
	            // Couldn't find it
	            defaultValue.getOrElse(null)
	        }
	    }
	}
	
	def jsonParser(json: JsValue, jsPath: List[String], defaultValue: Option[JsValue]): JsValue = jsPath match {
	    case List() => json
	    case js::trailPath => {
	        // Get the remaining value from the json
	        val newJson = (json \ js).asOpt[JsValue]
	        newJson match {
	            case Some(nj) => {
	                // Recurse into new JSON
	                jsonParser(nj, trailPath, defaultValue)
	            }
	            case None => {
	                // Couldn't find it, return the best we can
	                defaultValue match {
	                    case Some(value) => value
	                    case _ => json
	                }
	            }
	        }
	    }
	}
	
	def JsonStringToNormalString(value: JsString) = {
	    // Remove the annoying quotes
	   value.toString.drop(1).take(value.toString.size - 2)
	}
	
	/**
     * Replaces all field names with the values
     */
    def replaceFields(expression: String, datum: Map[String, Any]): String = {
        /**
         * Immutably replaces all json-paths with their value
         * @param find Boolean The current find, true if a match was found, false otherwise
         * @param matcher Matcher The matcher, used to get the current match
         * @param accumString String The currently accumulated String
         * @return String The string with all replacements executed
         */
        def immutableReplace(find: Boolean, matcher: Matcher, accumString: String): String = find match {
            case true => {
                // Get the JSON path, which is an array of strings separated by space
                val jsonPath = matcher.group(1).split(" ")
                // Get the actual value
                val value = {
                    // See what to do
		            if (datum(jsonPath.toList.head).isInstanceOf[JsValue])
		                util.jsonParser(datum(jsonPath.toList.head).asInstanceOf[JsValue], jsonPath.toList.drop(1), None)
		            else
		                util.fieldParser(datum, jsonPath.toList, None)
                }
                // Replace with the value
                immutableReplace(
                        matcher.find, matcher,
                        accumString.replaceFirst("\\[" + jsonPath.mkString(" ") + "\\]", "\"" + value + "\"")
                )
            }
            case false => accumString
        }
        // Define pattern to find everything in between [ and ]
        val matcher = Pattern.compile("\\[(.*?)\\]").matcher(expression)
        immutableReplace(matcher.find, matcher, expression)
    }
}

/**
 * Filters specific fields from the data tuple
 */
class FieldFilterProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Find out which fields we should extract
        val fieldList = (config \ "fields").as[List[JsObject]]
        new DataPacket(for (datum <- data.data) yield {
            val newData = (for {
                    fieldItem <- fieldList
                    default = (fieldItem \ "default").asOpt[JsValue]
                    fields = (fieldItem \ "path").as[List[String]]
                    fieldName = (fieldItem \ "result").as[String]
                    field = fields.head
                    if (fields.size > 0 && datum.contains(field))
            } yield {
                // See what to do
                if (datum(field).isInstanceOf[JsValue])
                    fieldName -> util.jsonParser(datum(field).asInstanceOf[JsValue], fields.drop(1), default)
                else
                    fieldName -> util.fieldParser(datum, fields, default)
            }).toMap
            
            newData
        })
    })
}

/**
 * Gets a JSON Object and fetches a single field to put it as top-level citizen of the data
 */
class JsonFetcherProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Find out which fields we should extract
        val fieldList = (config \ "fields").as[List[JsObject]]
        new DataPacket(for (datum <- data.data) yield {
            val newData = (for {
                    fieldItem <- fieldList
                    default = (fieldItem \ "default").asOpt[JsValue]
                    fields = (fieldItem \ "path").as[List[String]]
                    fieldName = (fieldItem \ "result").as[String]
                    field = fields.head
                    if (fields.size > 0 && datum.contains(field))
            } yield {
                // See what to do
                if (datum(field).isInstanceOf[JsValue])
                    fieldName -> util.jsonParser(datum(field).asInstanceOf[JsValue], fields.drop(1), default)
                else
                    fieldName -> util.fieldParser(datum, fields, default)
            }).toMap
            
            datum ++ newData
        })
    })
}

/**
 * Renames a single field
 */
class FieldRenameProcessor(resultName: String) extends BaseProcessor(resultName) {
    var fieldList: List[JsObject] = null
    
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        new DataPacket(for (datum <- data.data) yield {
            if (fieldList == null) {
    	        // Find out which fields we should extract
    	        fieldList = (config \ "fields").as[List[JsObject]]
            }
	        var mutableDatum = collection.mutable.Map(datum.toSeq: _*) 
	        for {
	                field <- fieldList
	                source = (field \ "source").as[String]
	                target = (field \ "target").as[String]
	        } {
	            // Get source value
	            val srcValue = datum(source)
	            // Replace
	            mutableDatum = mutableDatum - source + (target -> srcValue)
	        }
	        
	        mutableDatum.toMap
        })
    })
}

class InclusionProcessor(resultName: String) extends BaseProcessor(resultName) {
    var expression: String = null
    var expressionType: String = null
    var andOr: String = null
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        if (expression == null) {
            // Get the groovy expression that determines whether to include or exclude
            expression = (config \ "expression").as[String]
            // See if this is a simple or groovy expression
            expressionType = (config \ "type").as[String]
            // Set and/or
            andOr = (config \ "and_or").asOpt[String] match {
                case Some("or") => "or"
                case _ => "and"
            }
        }
        
        new DataPacket(for {
                datum <- data.data
                // See if we need to include this
                include = expressionType match {
                    case "groovy" => {
                        // For the expression, anything placed in between [ and ] is a field of our data
                    	val replacedExpression = util.replaceFields(expression, datum)
                    	
	                    try {
	                        Eval.me(replacedExpression).asInstanceOf[Boolean]
	                    } catch {
	                        case _: Throwable => true
	                    }
                    }
                    case "negate" => {
                        // This is a comma-separated list of field=val statements
                        val matches = expression.split(",").map(m => m.trim)
                        val evals = for (m <- matches) yield {
                            val split = m.split("=").map(s => s.trim)
                            // Get field and value and see if they match
                            datum(split(0)) == split(1)
                        }
                        // See if its and/or
                        if (andOr == "or") !evals.exists(elem => elem)
                        else evals.exists(elem => !elem)
                    }
                    case _ => {
                        // This is a comma-separated list of field=val statements
                        val matches = expression.split(",").map(m => m.trim)
                        val evals = (for (m <- matches) yield {
                            val split = m.split("=").map(s => s.trim)
                            // Get field and value and see if they match
                            datum(split(0)) == split(1)
                        }).toList
                        // See if its and/or
                        if (andOr == "or") evals.exists(elem => elem)
                        else !evals.exists{elem => !elem}
                    }
                }
                if (include)
        } yield {
            datum
        })
    })
}

/**
 * Streams data into a file and closes it when it's done
 */
class FileStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: BufferedWriter = null
    var fields = collection.mutable.Map[String, Int]()
    var fieldSep: String = null
    var lineSep: String = null
    
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.onEOF(() => {
        writer.flush
	    writer.close
    }) compose Enumeratee.map((data: DataPacket) => {
        // See if we need to initialize the buffered writer
       if (writer == null) {
           // Get the location of the file to write to
           val fileName = (config \ "file_name").as[String]
           val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
           
           // Get the field we need to write out
           (config \ "fields").as[List[String]].foreach {field => fields += field -> 1}
           fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
           lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")
           
           writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding))
       }

       new DataPacket(for (datum <- data.data) yield {
    	   // Write it
           val output = (datum collect {
                   case elem: (String, Any) if fields.contains(elem._1) => elem._2.toString
           }).mkString(fieldSep)
           
           writer.write(output + lineSep)
	       
	       datum
        })
	})
}

/**
 * Streams data into a file and closes it when it's done
 */
class BatchedFileStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: BufferedWriter = null
    var fields = collection.mutable.Map[String, Int]()
    var fieldSep: String = null
    var lineSep: String = null
    var batchSize: Int = 1
    var batch = new StringBuilder()
    var batchCount = 0
    
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.onEOF(() => {
        writer.flush
        writer.close
    }) compose Enumeratee.map((data: DataPacket) => {
        // See if we need to initialize the buffered writer
       if (writer == null) {
           // Get the location of the file to write to
           val fileName = (config \ "file_name").as[String]
           val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")
           
           // Get the field we need to write out
           (config \ "fields").as[List[String]].foreach {field => fields += field -> 1}
           fieldSep = (config \ "field_separator").asOpt[String].getOrElse(",")
           lineSep = (config \ "line_separator").asOpt[String].getOrElse("\r\n")
           
           // Get batch size
           batchSize = (config \ "batch_size").as[Int]
           
           writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding))
       }

       new DataPacket(for (datum <- data.data) yield {
           // Write it
           val output = (datum collect {
                   case elem: (String, Any) if fields.contains(elem._1) => elem._2.toString
           }).mkString(fieldSep)
           
           // Add to batch or write
           batch.append(output + lineSep)
           batchCount = batchCount + 1
           if (batchCount == batchSize) {
               writer.write(batch.toString)
               batch.clear
           }
           
           datum
        })
    })
}

/**
 * Converts all fields to CSV
 */
class CSVProcessor(resultName: String) extends BaseProcessor(resultName) {
	override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
	    // Set up writer
	    val sw = new StringWriter
	    val csvWriter = new CSVWriter(sw, ',', '"', '\\', "")
	    // Convert data to CSV
	    val newData = for (datum <- data.data) yield {
	        // Strings are a bit annoying here
	        val stringDatum = datum.map(someVal => someVal._2.toString)
	        csvWriter.writeNext(stringDatum.toArray)
	        val res = sw.toString
	       
	        datum + (resultName -> res)
	    }
	    // Close
	    csvWriter.close
	    sw.close
	    
	    new DataPacket(newData)
	})
}

/**
 * Writes CSV to a file
 */
class CSVWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    var writer: CSVWriter = null
    var wroteHeaders = false
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.onEOF(() => {
        writer.flush
	    writer.close
    }) compose Enumeratee.map((data: DataPacket) => {
        // See if we need to initialize the buffered writer
       if (writer == null) {
           // Get the location of the file to write to
           val fileName = (config \ "file_name").as[String]
           val encoding = (config \ "encoding").asOpt[String].getOrElse("utf-8")

           writer = new CSVWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding)), ';', '"', '\\')
       }
       // See if fields are specified
       val fields = (config \ "fields").asOpt[List[String]]

	    // Convert data to CSV
	    for (datum <- data.data) {
	        // Write out headers
	        if (!wroteHeaders) {
	            fields match {
	                case Some(flds) => writer.writeNext(flds.toArray)
	                case None => writer.writeNext(datum.map(elem => elem._1).toArray)
	            }
	        	wroteHeaders = true
	        }
	        
	        fields match {
	            case Some(fields) => {
	                val values = fields.map(someVal => datum(someVal).isInstanceOf[JsString] match {
	                    case true => util.JsonStringToNormalString(datum(someVal).asInstanceOf[JsString])
	                    case false => datum(someVal).toString
	                })
	                writer.writeNext(values.toArray)
	            }
	            case None => {
	                // Strings are a bit annoying here
			        val stringDatum = datum.map(someVal => someVal._2.isInstanceOf[JsString] match {
			            case true => util.JsonStringToNormalString(someVal._2.asInstanceOf[JsString])
			            case false => someVal._2.toString
			        })
	                writer.writeNext(stringDatum.toArray)
	            }
	        }
	    }
	    
	    data
	})
}

/**
 * Adds a field with a constant (static) value
 */
class FieldConstantAdderProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Get the value for the field
        val value = (config \ "value").as[JsString].value
        
        new DataPacket(for (datum <- data.data) yield {
	        datum + (resultName -> value.toString)
        })
    })
}

/**
 * Dumps the data to console
 */
class ConsoleWriterProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        data.data.foreach(datum => datum.foreach(dat => println(dat._1 + " -- " + dat._2)))
        println
        
        data
    })
}

/**
 * Implodes an array into a string
 */
class ImploderProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Get the field
        val fieldList = (config \ "fields").as[List[JsObject]]
        
        new DataPacket(for (datum <- data.data) yield {
            // Find out which fields we should extract
	        var mutableDatum = collection.mutable.Map(datum.toSeq: _*) 
	        for (fieldObject <- fieldList) {
	            // Get fields
	            val fields = (fieldObject \ "path").as[List[String]]
	            val sep = (fieldObject \ "separator").as[String]
	            // Get field name
	            val field = fields.head
	            // Get the actual value
	            val value = {
	                if (datum(field).isInstanceOf[JsValue])
	                    util.jsonParser(datum(field).asInstanceOf[JsValue], fields.drop(1), None).as[List[String]]
	                else {
	                	val someVal = util.fieldParser(datum, fields, None)
	                	if (someVal.isInstanceOf[Array[String]]) someVal.asInstanceOf[Array[String]].toList
	                	else if (someVal.isInstanceOf[Seq[String]]) someVal.asInstanceOf[Seq[String]].toList
	                	else someVal.asInstanceOf[List[String]]
	                }
	            }
	            // Replace
	            mutableDatum += field -> value.mkString(sep)
	        }
	        mutableDatum.toMap
        })
    })
}

/**
 * Implodes an array  of JSON object-fields into a string
 */
class JsObjectImploderProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Get the field
        val fieldList = (config \ "fields").as[List[JsObject]]
        
        new DataPacket(for (datum <- data.data) yield {
            // Find out which fields we should extract
	        var mutableDatum = collection.mutable.Map(datum.toSeq: _*) 
	        for (fieldObject <- fieldList) {
	            // Get fields
	            val fields = (fieldObject \ "path").as[List[String]]
	            val subpath = (fieldObject \ "subpath").as[List[String]]
	            val sep = (fieldObject \ "separator").as[String]
	            // Get field name
	            val field = fields.head
	            // Get the actual value
	            val values = {
	                if (datum(field).isInstanceOf[JsArray]) util.jsonParser(datum(field).asInstanceOf[JsValue], fields.drop(1), None).as[List[JsObject]]
	                else List[JsObject]()
	            }
	            // Now iterate over the objects
	            val gluedValue = values.map(value => {
	                util.JsonStringToNormalString(util.jsonParser(value, subpath, None).as[JsString])
	            }).mkString(sep)
	            // Replace
	            mutableDatum += field -> gluedValue
	        }
	        mutableDatum.toMap
        })
    })
}

/**
 * Flattens a map object
 */
class FlattenerProcessor(resultName: String) extends BaseProcessor(resultName) {
    def recursiveFlattener(mapping: Map[String, Any], currentKey: String, sep: String): Map[String, Any] = {
        // Get the values of the map
        (for (mapElem <- mapping.toList) yield {
            val key = mapElem._1
            val value = mapElem._2
            
            value.isInstanceOf[Map[String, Any]] match {
                case true => {
		            // Get the sub fields recursively
                    recursiveFlattener(value.asInstanceOf[Map[String, Any]], currentKey + sep + key, sep)
		        }
		        case false => {
		            Map(currentKey + sep + key -> value)
		        }
            }
        }).toList.foldLeft(Map[String, Any]())(_ ++ _)
    }
    
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Get the field to flatten
        val fieldList = (config \ "fields").as[List[String]]
        val separator = (config \ "separator").as[String]
        
        new DataPacket(for (datum <- data.data) yield {
            // Find out which fields we should extract
	        var mutableDatum = collection.mutable.Map(datum.toSeq: _*) 
	        for (fieldName <- fieldList) {
	            // Get the value
	            val value = {
	                try {
	                    recursiveFlattener(datum(fieldName).asInstanceOf[Map[String, Any]], fieldName, separator)
	                } catch {
	                    case e: Exception => {
	                        e.printStackTrace()
	                        Map[String, Any]()
	                    }
	                }
	            }
	            
	            // Replace
	            mutableDatum ++= value
	        }
	        mutableDatum.toMap
        })
    })
}

/**
 * Invokes a new generator
 */
class GeneratorConfigProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(1 seconds)
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Get the name of the config file
        val nextName = (config \ "name").as[String]
        // See if we need to populate the config file
        val fieldsToAdd = (config \ "add_fields").asOpt[List[JsObject]]
        
        // See if we need to add the config or not
        fieldsToAdd match {
            case Some(fields) => {
                // Add fields to our config
                val mapToAdd = (for (field <- fields) yield {
                    // Get source and target name
                    val source = (field \ "source").as[String]
                    val target = (field \ "target").as[String]
                    
                    // Add to our new config, we assume only one element, otherwise this makes little sense
                    target -> data.data.head(source).toString
                }).toMap
                
                // Build the map and turn into JSON config
                val newConfig = (config \ "config").as[JsObject] ++ Json.toJson(mapToAdd).asInstanceOf[JsObject]
                
                // Invoke the new generator with custom config
                try {
                    val fut = Akka.system.actorSelection("user/TuktuDispatcher") ? Identify(None)
                    val dispActor = Await.result(fut.mapTo[ActorIdentity], 2 seconds).getRef
                    dispActor ! new controllers.asyncDispatchRequest(nextName, Some(newConfig), false, false)
                } catch {
                    case e: TimeoutException => {} // skip
                    case e: NullPointerException => {}
                }
            }
            case None => {
                // Invoke the new generator, as-is
		        try {
			    	val fut = Akka.system.actorSelection("user/TuktuDispatcher") ? Identify(None)
		            val dispActor = Await.result(fut.mapTo[ActorIdentity], 2 seconds).getRef
			    	dispActor ! new controllers.asyncDispatchRequest(nextName, None, false, false)
			    } catch {
		    	    case e: TimeoutException => {} // skip
		    	    case e: NullPointerException => {}
			    }
            }
        }
        
	    // We can still continue with out data
        data
    })
}

/**
 * This class is used to always have an actor present when data is to be streamed in sync
 */
class SyncStreamForwarder() extends Actor with ActorLogging {
    implicit val timeout = Timeout(5 seconds)
    
    var remoteGenerator: ActorRef = null
    var sync: Boolean = false
    
    def receive() = {
        case setup: (ActorRef, Boolean) => {
            remoteGenerator = setup._1
            sync = setup._2
            sender ! "ok"
        }
        case dp: DataPacket => sync match { 
            case false => remoteGenerator ! dp
            case true => {
                sender ! Await.result((remoteGenerator ? dp).mapTo[DataPacket], timeout.duration)
            }
        }
        case sp: StopPacket => remoteGenerator ! StopPacket()
    }
}

/**
 * Invokes a new generator
 */
class GeneratorStreamProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(5 seconds)
    
    val forwarder = Akka.system.actorOf(Props[SyncStreamForwarder])
    var init = false
    var sync: Boolean = false
    
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.onEOF(() => {
        forwarder ! new StopPacket()
        forwarder ! PoisonPill
    }) compose Enumeratee.map(data => {
        if (!init) {
            // Get the name of the config file
            val nextName = (config \ "name").as[String]
            // Node to execute on
            val node = (config \ "node").asOpt[String]
            // Get the processors to send data into
            val next = (config \ "next").as[List[String]]
            // Get the actual config, being a list of processors
            val processors = (config \ "processors").as[List[JsObject]]
            
            sync = (config \ "sync").asOpt[Boolean].getOrElse(false)
            
            // Manipulate config and set up the remote actor
            val customConfig = Json.obj(
                "generators" -> List((Json.obj(
                    "name" -> {
                        sync match {
                            case true => "tuktu.generators.SyncStreamGenerator"
                            case false => "tuktu.generators.AsyncStreamGenerator"
                        }
                    },
                    "result" -> "",
                    "config" -> Json.obj(),
                    "next" -> next
                ) ++ {
                    node match {
                        case Some(n) => Json.obj("node" -> n)
                        case None => Json.obj()
                    }
                })),
                "processors" -> processors
            )
            
            // Send a message to our Dispatcher to create the (remote) actor and return us the actorref
            try {
                val fut = Akka.system.actorSelection("user/TuktuDispatcher") ? Identify(None)
                val dispActor = Await.result(fut.mapTo[ActorIdentity], timeout.duration).getRef
                
                // Set up actor and get ref
                val refFut = sync match {
                    case true => dispActor ? new controllers.syncDispatchRequest(nextName, Some(customConfig), false, true)
                    case false => dispActor ? new controllers.asyncDispatchRequest(nextName, Some(customConfig), false, true)
                }
                val remoteGenerator = Await.result(refFut.mapTo[ActorRef], timeout.duration)
                Await.result(forwarder ? (remoteGenerator, sync), timeout.duration)
            } catch {
                case e: TimeoutException => {} // skip
                case e: NullPointerException => {}
            }
            init = true
        }
        
        // Send the result to the generator
        val newData = sync match {
            case true => {
                // Get the result from the generator
                val dataFut = forwarder ? data
                Await.result(dataFut.mapTo[DataPacket], timeout.duration)
            }
            case false => {
                forwarder ! data
                data
            }
        }
        
        // We can still continue with out data
        newData
    })
}

/**
 * Actor that deals with parallel processing
 * 
 */
class ParallelProcessorActor(processor: Enumeratee[DataPacket, DataPacket]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(1 seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    
    val sendBackEnum: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
        // Get the actor ref and acutal data
        val actorRef = dp.data.head("ref").asInstanceOf[ActorRef]
        val newData = new DataPacket(dp.data.drop(1))
        actorRef ! newData
        newData
    })
    enumerator |>> (processor compose sendBackEnum) &>> sinkIteratee
    
    def receive() = {
        case data: DataPacket => {
            // We add the ActorRef to the datapacket because we need it later on
            channel.push(new DataPacket(Map("ref" -> sender)::data.data))
        }
    }
}

/**
 * Executes a number of processor-flows in parallel
 */
class ParallelProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(1 seconds)
    
    var actors: List[ActorRef] = null
    var merger: Method = null
    var mergerClass: Any = null
    
    /**
     * Taken from dispatcher; recursively pipes Enumeratees
     */
    def buildEnums (
            nextId: List[String],
            processorMap: Map[String, (Enumeratee[DataPacket, DataPacket], List[String])]
    ): List[Enumeratee[DataPacket, DataPacket]] = {
        /**
         * Function that recursively builds the tree of processors
         */
        def buildEnumsHelper(
                next: List[String],
                accum: List[Enumeratee[DataPacket, DataPacket]],
                iterationCount: Integer
        ): List[Enumeratee[DataPacket, DataPacket]] = {
            if (iterationCount > 500) {
                // Awful lot of enumeratees... cycle?
                throw new Exception("Possible cycle detected in config file. Aborted")
            }
            else {
                next match {
                    case List() => {
                        // We are done, return accumulator
                        accum
                    }
                    case id::List() => {
                        // This is just a pipeline, get the processor
                        val proc = processorMap(id)
                        
                        buildEnumsHelper(proc._2, {
                            if (accum.isEmpty) List(proc._1)
                            else accum.map(enum => enum compose proc._1)
                        }, iterationCount + 1)
                    }
                    case nextList => {
                        // We have a branch here and need to expand the list of processors
                        (for (id <- nextList) yield {
                            val proc = processorMap(id)
                            
                            buildEnumsHelper(proc._2, {
                                if (accum.isEmpty) List(proc._1)
                                else accum.map(enum => enum compose proc._1)
                            }, iterationCount + 1)
                        }).flatten
                    }
                }
            }
        }
        
        // Build the enums
        buildEnumsHelper(nextId, List(), 0)
    }
    
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Get hte processors
        if (actors == null) {
            // Process config
            val pipelines = (config \ "processors").as[List[JsObject]]
            
            // Set up the merger
            val mergerProcClazz = Class.forName((config \ "merger").as[String])
            mergerClass = mergerProcClazz.getConstructor().newInstance()
            merger = mergerProcClazz.getDeclaredMethods.filter(m => m.getName == "merge").head
            
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
                    
                    // Instantiate processor
                    val procClazz = Class.forName(processorName)
                    val iClazz = procClazz.getConstructor(classOf[String]).newInstance(resultName)
                    val method = procClazz.getDeclaredMethods.filter(m => m.getName == "processor").head
                    val proc = method.invoke(iClazz, processorConfig).asInstanceOf[Enumeratee[DataPacket, DataPacket]]
                    
                    // Return map
                    processorId -> (proc, next)
                }).toMap
                
                // Build the processor pipeline for this generator
                val processor = buildEnums(List(start), processorMap).head
                // Set up the actor that will execute this processor
                Akka.system.actorOf(Props(classOf[ParallelProcessorActor], processor))
            }
        }
        
        // Send data to actors
        val futs = for (actor <- actors) yield
            actor ? data
        // Get the results
        val results = for (fut <- futs) yield
            Await.result(fut.mapTo[DataPacket], timeout.duration)
            
        // Apply the merger
        merger.invoke(mergerClass, results).asInstanceOf[DataPacket]
    })
    
}