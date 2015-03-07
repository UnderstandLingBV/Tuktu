package tuktu.processors.bucket.concurrent.statistics

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import tuktu.api._
import tuktu.processors.bucket.BaseBucketProcessor
import tuktu.processors.bucket.SortProcessor

object StatHelper {
    def anyToDouble(num: Any) = num match {
        case a: Int => a.toDouble
        case a: Integer => a.toDouble
        case a: Double => a
        case a: Long => a.toDouble
        case a: Float => a.toDouble
    }
}

/**
 * Computes the mean over a field containing numerical values
 */
class MeanProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields = List[String]()
    
    override def initialize(config: JsObject) = {
        fields = (config \ "fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        var sums = collection.mutable.Map[String, Double]()
        fields.foreach(field => sums += field -> 0.0)
        
        // Go over the data
        data.foreach(datum => {
            // Update sums
            sums.foreach(sum => sum match {
                case (field, value) => {
                    sums(field) = value + StatHelper.anyToDouble(datum(field))
                }
            })
        })
        
        // Build result
        val n = data.size
        List((for ((field, sum) <- sums) yield {
            field -> (sum / n)
        }).toMap)
    }
}

/**
 * Computes the median over a field containing numerical values
 */
class MedianProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields = List[String]()
    
    override def initialize(config: JsObject) = {
        fields = (config \ "fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        List((for (field <- fields) yield {
            // We must stor the data before continuing
            val sp = new SortProcessor("")
            sp.initialize(Json.obj("field" -> field))
            val sortedData = sp.doProcess(data)
            
            // Find the mid element
            val n = sortedData.size
            if (n % 2 == 0) {
                // Get the two elements and average them
                val n2 = n / 2
                val n1 = n2 - 1
                field -> ((StatHelper.anyToDouble(sortedData(n1)(field)) + StatHelper.anyToDouble(sortedData(n2)(field))) / 2)
            } else field -> StatHelper.anyToDouble(sortedData(n / 2)(field))
        }).toMap)
    }
}

/**
 * Computes the mode over a field containing numerical values
 */
class ModeProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields = List[String]()
    var valueCountMapper = collection.mutable.Map[String, collection.mutable.Map[Any, Int]]()
    
    override def initialize(config: JsObject) = {
        fields = (config \ "fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        data.foreach(datum => {
            fields.foreach(field => {
                // Initialize if required
                if (!valueCountMapper.contains(field)) valueCountMapper += field -> collection.mutable.Map[Any, Int]()
                
                // Get value
                val value = datum(field)
                if (!valueCountMapper(field).contains(value)) valueCountMapper(field) += value -> 1
                else valueCountMapper(field)(value) += 1
            })
        })
        
        // Find all most frequently occurring elements
        for (
                field <- fields
                if (valueCountMapper.contains(field))
        ) yield {
            val values = valueCountMapper(field)
            val mostFrequent = values.maxBy(elem => elem._2)
            
            // Return element and the occurence count
            Map(
                    field -> mostFrequent._2, resultName -> mostFrequent._1
            )
        }
    }
}

/**
 * Computes the midrange over a field containing numerical values
 */
class MidrangeProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    override def initialize(config: JsObject) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        null
    }
}

/**
 * Computes the standard deviation over a field containing numerical values
 */
class StDevProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    override def initialize(config: JsObject) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        null
    }
}

/**
 * Computes the variance over a field containing numerical values
 */
class VarProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    override def initialize(config: JsObject) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        null
    }
}

/**
 * Computes correlation between a number of fields of numerical values
 */
class CorrelationProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    override def initialize(config: JsObject) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        null
    }
}

/**
 * Computes covariance between a number of fields of numerical values
 */
class CovarianceProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    override def initialize(config: JsObject) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        null
    }
}