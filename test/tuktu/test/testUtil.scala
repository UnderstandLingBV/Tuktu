package tuktu.test

object testUtil {
    /**
     * Inspects and matches an obtained map with an expected map
     */
    def inspectMaps(obtained: Map[String, Any], expected: Map[String, Any]): Boolean = {
        // Check keys first
        if (!obtained.keys.toList.diff(expected.keys.toList).isEmpty)
            false
        else {
            // Keys match, inspect all values
            (for ((key, value) <- obtained) yield {
                inspectValue(value, expected(key))
            }).foldLeft(true)(_ && _)
        }
    }
    
    /**
     * Function to inspect a single value
     */
    def inspectValue(obtained: Any, expected: Any): Boolean = {
        // Check types first
        try {
            obtained match {
                case v: Map[Any, Any] => {
                    val w = expected.asInstanceOf[Map[Any, Any]]
                    v.keys.toList.diff(w.keys.toList).isEmpty && v.forall(elem => inspectValue(elem._2, w(elem._1)))
                }
                case v: List[Any] => {
                    val w = expected.asInstanceOf[List[Any]]
                    v.zip(w).forall(elems => inspectValue(elems._1, elems._2))
                }
                // Rounding errors
                case v: Double => Math.abs(v - expected.asInstanceOf[Double]) < 0.000000001
                case _: Any => expected match {
                    // Rounding errors
                    case v: Double => Math.abs(v - obtained.asInstanceOf[Double]) < 0.000000001
                    case _: Any => obtained.toString == expected.toString
                }
            }
        } catch {
            // TODO: Maybe differentiate on types of exceptions?
            case e: Throwable => {
                e.printStackTrace()
                false
            }
        }
    }
}