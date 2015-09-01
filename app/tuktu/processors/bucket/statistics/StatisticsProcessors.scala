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
     * Gets means
     */
    def getMeans(data: List[Map[String, Any]], fields: List[String]): Map[String, Double] = {
        var sums = collection.mutable.Map[String, Double]().withDefaultValue(0.0)

        // Go over the data and update sums for each field
        for (datum <- data; field <- fields) {
            sums(field) += anyToDouble(datum(field))
        }

        // Return the means
        sums.map(sum => sum._1 -> sum._2 / data.size).toMap
    }

    /**
     * Gets variances
     */
    def getVariances(data: List[Map[String, Any]], fields: List[String]): Map[String, Double] = {
        // Get the means
        val means = getMeans(data, fields)

        // Now compute the sums over the quadratic of the difference with the mean
        var varSums = collection.mutable.Map[String, Double]().withDefaultValue(0.0)
        for (datum <- data; field <- fields) {
            varSums(field) += math.pow(anyToDouble(datum(field)) - means(field), 2)
        }

        // Return variances
        varSums.map(sum => sum._1 -> sum._2 / data.size).toMap
    }
}

/**
 * Computes the mean over a field containing numerical values
 */
class MeanProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields = List[String]()

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Get and return means
        List(StatHelper.getMeans(data, fields))
    }
}

/**
 * Computes the median over a field containing numerical values
 */
class MedianProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields = List[String]()

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        List((for (field <- fields) yield field -> {
            // We must sort the data first
            val sortedData = data.map(datum => StatHelper.anyToDouble(datum(field))).sorted

            // Find the mid element
            val n = sortedData.size
            if (n % 2 == 0) {
                // Get the two elements and average them
                val n2 = n / 2
                val n1 = n2 - 1
                (sortedData(n1) + sortedData(n2)) / 2
            } else
                sortedData((n - 1) / 2)
        }).toMap)
    }
}

/**
 * Computes the mode over a field containing numerical values
 */
class ModeProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields = List[String]()

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        for (field <- fields) yield {
            // Initialize the value counter
            var valueCounter = collection.mutable.Map[Any, Int]().withDefaultValue(0)

            // Count values
            for (datum <- data) { valueCounter(datum(field)) += 1 }

            // Return most frequent element and its occurrence count
            val mostFrequent = valueCounter.maxBy(_._2)
            Map(field -> mostFrequent._2, resultName -> mostFrequent._1)

        }
    }
}

/**
 * Computes the midrange over a field containing numerical values
 */
class MidrangeProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields = List[String]()
    // Keep track of min, max
    var mins = collection.mutable.Map[String, Double]().withDefaultValue(Double.MaxValue)
    var maxs = collection.mutable.Map[String, Double]().withDefaultValue(Double.MinValue)

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Iterate over data and fields
        for (datum <- data; field <- fields) {
            // Get the field we are after and update min/max
            val value = StatHelper.anyToDouble(datum(field))
            mins(field) = math.min(mins(field), value)
            maxs(field) = math.max(maxs(field), value)
        }

        // Return the midrange
        List((for (field <- fields) yield {
            field -> (mins(field) + maxs(field)) / 2
        }).toMap)
    }
}

/**
 * Computes the standard deviation over a field containing numerical values
 */
class StDevProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        // Get the fields to compute StDev over
        fields = (config \ "fields").as[List[String]]
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Get variances
        val vars = StatHelper.getVariances(data, fields)

        // Sqrt them to get StDevs
        List(vars.map(v => v._1 -> math.sqrt(v._2)))
    }
}

/**
 * Computes the variance over a field containing numerical values
 */
class VarProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        // Get the fields to compute variance over
        fields = (config \ "fields").as[List[String]]
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Get and return variances
        List(StatHelper.getVariances(data, fields))
    }
}

/**
 * Computes correlation coefficient between a number of fields of numerical values
 */
class CorrelationProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _
    var pValuesField: Option[String] = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
        pValuesField = (config \ "p_values").asOpt[String]
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Collect all data
        val values = (for (field <- fields) yield {
            (for (datum <- data) yield StatHelper.anyToDouble(datum(field))).toArray
        }).toArray

        // Compute correlations
        val pc = new PearsonsCorrelation()
        val correlations = pc.computeCorrelationMatrix(values)

        // Compute P-values if request and return the matrix
        pValuesField match {
            case Some(field) => {
                // Get the P-values
                val pValues = pc.getCorrelationPValues()
                List(Map(
                        resultName -> correlations.getData,
                        field -> pValues.getData
                ))
            }
            case None => List(Map(resultName -> correlations.getData))
        }
    }
}

/**
 * Computes covariance between a number of fields of numerical values
 */
class CovarianceProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

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

/**
 * Computes correlation between a number of fields of numerical values
 */
class CorrelationMatrixProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _

    override def initialize(config: JsObject) {
        fields = (config \ "fields").as[List[String]]
    }

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        // Collect all data
        val values = (for (field <- fields) yield {
            (for (datum <- data) yield StatHelper.anyToDouble(datum(field))).toArray
        }).toArray

        // Compute correlations
        val pc = new PearsonsCorrelation()
        // Compute all the correlations between each value
        val correlationMatrix = values.map(x => values.map(y => pc.correlation(x, y)).toSeq).toSeq
        
        List(Map(resultName -> correlationMatrix))        
    }
}