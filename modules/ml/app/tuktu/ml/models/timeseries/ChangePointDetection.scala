package tuktu.ml.models.timeseries

import org.apache.commons.lang3.ArrayUtils
import java.util.Arrays

/**
 * Implementation from https://github.com/addthis/hydra. Translated into Scala
 * (See https://github.com/addthis/hydra/blob/ffd708a1078f2bcb24cc873d4f12ef18f9d410b4/hydra-data/src/main/java/com/addthis/hydra/data/util/FindChangePoints.java)
 */

object ChangePointType extends Enumeration {
    type ChangePointType = Value
    val RISE, FALL, START, STOP, PEAK = Value
}

/**
 *         Tools for finding change points in an integer array.
 */
object ChangePointDetection {
    import ChangePointType._
    case class ChangePoint(
            size: Double,
            index: Int,
            cpType: ChangePointType
    )
    
    def changePointToMap(cp: ChangePoint) = {
        Map(
                "size" -> cp.size,
                "index" -> cp.index,
                "type" -> cp.cpType.toString
        )
    }
    
    def apply(data: List[Double], minChange: Double, minRatio: Double, minZScore: Double, inactiveThreshold: Double, windowSize: Int) =
        findSignificantPoints(data.toArray, minChange, minRatio, minZScore, inactiveThreshold, windowSize)
        
    def apply(data: Array[Double], minChange: Double, minRatio: Double, minZScore: Double, inactiveThreshold: Double, windowSize: Int) =
        findSignificantPoints(data, minChange, minRatio, minZScore, inactiveThreshold, windowSize)

    /**
     * Finds places where the data changed dramatically, either sustained or "instantaneously"
     *
     * @param data The array of integers in which to search
     * @return A list of pairs of integers of the form (index, size)
     */
    def findSignificantPoints(data: Array[Double], minChange: Double, minRatio: Double, minZScore: Double, inactiveThreshold: Double, windowSize: Int) = {
        val rv = collection.mutable.ListBuffer[ChangePoint]()
        rv ++= findAndSmoothOverPeaks(data, minChange, minZScore, windowSize)
        rv ++= findChangePoints(data, minChange, minRatio, minZScore, inactiveThreshold, windowSize)
        rv.toList
    }

    def findChangePoints(data: Array[Double], minChange: Double, minRatio: Double, minZScore: Double, inactiveThreshold: Double, windowSize: Int) = {
        val rvList = collection.mutable.ListBuffer[ChangePoint]()
        for (i <- 2 to data.size - 1) {
            val startIndex = Math.max(i - windowSize + 1, 0)
            val currSlice = data.drop(startIndex).take(i - startIndex)
            val nextValue = data(i)
            val predicted = linearPredictNext(currSlice)
            val diff = nextValue - predicted
            val zScoreDiff = diff / sd(currSlice)
            val changeRatio = -1 + (nextValue.toDouble) / Math.max(predicted, 1.0)
            if (Math.abs(zScoreDiff) > minZScore && Math.abs(diff) > minChange && Math.abs(changeRatio) > minRatio) {
                val cpType = chooseTypeForChange(mean(currSlice), nextValue, inactiveThreshold)
                rvList += new ChangePoint(diff, i, cpType)
            }
        }
        
        rvList.toList
    }

    def chooseTypeForChange(before: Double, after: Double, inactiveThreshold: Double) = {
        if (before > after)
            if (after > inactiveThreshold) ChangePointType.FALL else ChangePointType.STOP
        else
            if (before < inactiveThreshold) ChangePointType.START else ChangePointType.RISE
    }

    def findAndSmoothOverPeaks(data: Array[Double], minChange: Double, minZscore: Double, width: Int) = {
        val rvList = collection.mutable.ListBuffer[ChangePoint]()
        for (i <- 0 to data.size - 1) {
            val leftEndpoint = Math.max(0, i - width)
            val rightEndpoint = Math.min(i + width, data.length)
            val neighborhood = data.drop(leftEndpoint).take(rightEndpoint - leftEndpoint)
            val neighborhoodWithout = Arrays.copyOfRange(data, leftEndpoint, i) ++ Arrays.copyOfRange(data, i + 1, rightEndpoint)
            if (sd(neighborhood) > minZscore * sd(neighborhoodWithout)) {
                val change = data(i) - mean(neighborhoodWithout)
                if (Math.abs(change) > minChange) {
                    rvList += new ChangePoint(change, i, ChangePointType.PEAK)
                    data(i) = mean(neighborhoodWithout)
                }
            }
        }
        
        rvList.toList
    }

    def mean(doubles: Array[Double]) = doubles.sum / doubles.size

    def sd(doubles: Array[Double]) = {
        val m = mean(doubles)
        var sumSquareResiduals = 0.0
        for (z <- doubles)
            sumSquareResiduals += Math.pow(m - z, 2)
            
        Math.max(Math.sqrt(sumSquareResiduals), .0001)
    }

    def linearPredictNext(data: Array[Double]) = {
        val xx = collection.mutable.ListBuffer[Double]()
        val xy = collection.mutable.ListBuffer[Double]()
        for (i <- 0 to data.size - 1) {
            xx += i * i
            xy += i * data(i)
        }
        val meanx = 0.5 * (data.size - 1.0)
        val slope = (mean(xy.toArray) - meanx * mean(data)) / (mean(xx.toArray) - Math.pow(meanx, 2))
        val intercept = mean(data) - slope * meanx
        
        slope * data.size + intercept
    }
}
