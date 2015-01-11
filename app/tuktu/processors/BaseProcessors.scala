package tuktu.processors

import java.io._
import java.lang.reflect.Method
import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import au.com.bytecode.opencsv.CSVWriter
import groovy.util.Eval
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json._
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import tuktu.api._

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
                    fieldName -> utils.util.jsonParser(datum(field).asInstanceOf[JsValue], fields.drop(1), default)
                else
                    fieldName -> utils.util.fieldParser(datum, fields, default)
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
                    fieldName -> utils.util.jsonParser(datum(field).asInstanceOf[JsValue], fields.drop(1), default)
                else
                    fieldName -> utils.util.fieldParser(datum, fields, default)
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
                        // Replace expression with values
                    	val replacedExpression = tuktu.api.utils.evaluateTuktuString(expression, datum)
                    	
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
	                    utils.util.jsonParser(datum(field).asInstanceOf[JsValue], fields.drop(1), None).as[List[String]]
	                else {
	                	val someVal = utils.util.fieldParser(datum, fields, None)
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
	                if (datum(field).isInstanceOf[JsArray]) utils.util.jsonParser(datum(field).asInstanceOf[JsValue], fields.drop(1), None).as[List[JsObject]]
	                else List[JsObject]()
	            }
	            // Now iterate over the objects
	            val gluedValue = values.map(value => {
	                utils.util.JsonStringToNormalString(utils.util.jsonParser(value, subpath, None).as[JsString])
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