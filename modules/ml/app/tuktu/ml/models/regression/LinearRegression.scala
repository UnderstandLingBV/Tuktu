package tuktu.ml.models.regression

import tuktu.ml.models.BaseModel
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Implements a wrapper around apache common math regression
 */
class LinearRegression() extends BaseModel {
    var regression = new OLSMultipleLinearRegression
    var coefficients: Array[Double] = _
    
    private var currentData = Array.empty[Array[Double]]
    private var currentLabels = Array.empty[Double]
    
    /**
     * Adds data to our linear regression model
     */
    def addData(data: Array[Array[Double]], labels: Array[Double]) {
        currentData ++= data
        currentLabels ++= labels
        regression.newSampleData(currentLabels, currentData)
    }
    
    def calculateEstimation(x: Double, coe: Array[Double]) = {
        var result: Double = 0.0
        for (i <- 0 to coe.length - 1)
            result += coe(i) * Math.pow(x, i)
        result
    }

    /**
     * Predicts a Y-value for X-values
     */
    def classify(data: Seq[Double], estimate: Boolean) = {
        if (estimate)
            coefficients = regression.estimateRegressionParameters()
        data.zip(coefficients).foldLeft(0.0)((s, z) => s + z._1 * z._2)
    }
    
    override def serialize(filename: String) = {
        // Write out model
        coefficients = regression.estimateRegressionParameters()
        val oos = new ObjectOutputStream(new FileOutputStream(filename))
        oos.writeObject(coefficients)
        oos.close
    }
    
    override def deserialize(filename: String) = {
        val ois = new ObjectInputStream(new FileInputStream(filename))
        coefficients = ois.readObject.asInstanceOf[Array[Double]]
        ois.close
    }
}