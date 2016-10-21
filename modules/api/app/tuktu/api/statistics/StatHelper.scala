package tuktu.api.statistics

object StatHelper {
    def anyToDouble(num: Any) = num match {
        case a: Int     => a.toDouble
        case a: Integer => a.toDouble
        case a: Double  => a
        case a: Long    => a.toDouble
        case a: Float   => a.toDouble
        case a: Any     => a.toString.toDouble
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
        sums.mapValues { sum => sum / data.size }.toMap
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
        varSums.mapValues { sum => sum / data.size }.toMap
    }
}