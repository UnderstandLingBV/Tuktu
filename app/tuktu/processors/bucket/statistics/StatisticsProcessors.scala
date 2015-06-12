package tuktu.processors.bucket.statistics

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import tuktu.api._
import tuktu.processors.bucket.BaseBucketProcessor
import tuktu.processors.bucket.SortProcessor
import org.apache.commons.math3.stat.correlation.Covariance
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation

object StatHelper {
    def anyToDouble(num: Any) = num match {
        case a: Int => a.toDouble
        case a: Integer => a.toDouble
        case a: Double => a
        case a: Long => a.toDouble
        case a: Float => a.toDouble
        case a: Any => a.toString.toDouble
    }
    
    /**
     * Gets variance
     */
    def getVarSums(data: List[Map[String, Any]], fields: List[String]) = {
        // First compute the means
        var sums = collection.mutable.Map[String, Double]()
        
        // Go over the data
        data.foreach(datum => {
            // Update sums
            fields.foreach(field => sums contains field match {
                case true => sums(field) = sums(field) + StatHelper.anyToDouble(datum(field))
                case false => sums(field) = StatHelper.anyToDouble(datum(field))
            })
        })
        
        // Now compute the sums over the quadratic of the difference with the mean
        var varSums = collection.mutable.Map[String, Double]()
        data.foreach(datum => {
            // Update sums
            fields.foreach(field => varSums contains field match {
                case true => varSums(field) = varSums(field) + Math.pow(StatHelper.anyToDouble(datum(field)) - sums(field) / data.size, 2)
                case false => varSums(field) = Math.pow(StatHelper.anyToDouble(datum(field)) - sums(field) / data.size, 2)
            })
        })
        
        varSums.toMap
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
        
        // Go over the data
        data.foreach(datum => {
            // Update sums
            fields.foreach(field => sums contains field match {
                case true => sums(field) = sums(field) + StatHelper.anyToDouble(datum(field))
                case false => sums(field) = StatHelper.anyToDouble(datum(field))
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
    var fields = List[String]()
    // Keep track of min, max
    var mins = collection.mutable.Map[String, Double]()
    var maxs = collection.mutable.Map[String, Double]()
    
    override def initialize(config: JsObject) = {
        fields = (config \ "fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Iterate over data and fields
        for (datum <- data; field <- fields) {
            // Initialize
            if (!mins.contains(field)) mins += field -> Double.MaxValue
            if (!maxs.contains(field)) maxs += field -> Double.MinValue
            
            // Get the field we are after and see if its a new min/max
            val value = StatHelper.anyToDouble(datum(field))
            if (value < mins(field)) mins(field) = value
            if (value > maxs(field)) maxs(field) = value
        }
        
        // Return the midrange
        List(
            maxs.map(fieldMax => fieldMax._1 -> ((fieldMax._2 + mins(fieldMax._1)) / 2)).toMap
        )
    }
}

/**
 * Computes the standard deviation over a field containing numerical values
 */
class StDevProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _
    
    override def initialize(config: JsObject) = {
        // Get the fields to compute StDev over
        fields = (config \ "fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Get variances
        val vars = StatHelper.getVarSums(data, fields)
        
        // Sqrt them to get StDevs
        List(vars.map(v => v._1 -> Math.sqrt(v._2 / data.size)))
    }
}

/**
 * Computes the variance over a field containing numerical values
 */
class VarProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _
    
    override def initialize(config: JsObject) = {
        // Get the fields to compute variance over
        fields = (config \ "fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Get variances
        val vars = StatHelper.getVarSums(data, fields)
        
        // Sqrt them to get StDevs
        List(vars.map(v => v._1 -> v._2 / data.size))
    }
}

/**
 * Computes correlation between a number of fields of numerical values
 */
class CorrelationProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _
    
    override def initialize(config: JsObject) = {
        fields = (config \ "fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Collect all data
        val values = (for (field <- fields) yield {
            (for (datum <- data) yield StatHelper.anyToDouble(datum(field))).toArray
        }).toArray
        
        // Compute correlations
        val correlations = new PearsonsCorrelation().computeCorrelationMatrix(values)
        
        // Return the matrix
        List(Map(resultName -> correlations.getData))
    }
}

/**
 * Computes covariance between a number of fields of numerical values
 */
class CovarianceProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _
    
    override def initialize(config: JsObject) = {
        fields = (config \ "fields").as[List[String]]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Collect all data
        val values = (for (field <- fields) yield {
            (for (datum <- data) yield StatHelper.anyToDouble(datum(field))).toArray
        }).toArray
        
        // Compute correlations
        val covariances = new Covariance(values).getCovarianceMatrix
        
        // Return the matrix
        List(Map(resultName -> covariances.getData))
    }
}