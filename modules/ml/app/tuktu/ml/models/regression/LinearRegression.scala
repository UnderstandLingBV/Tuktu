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
class LinearRegression extends BaseModel {
    var regression = new OLSMultipleLinearRegression
    
    private var currentData: Array[Array[Double]] = _
    private var currentLabels: Array[Double] = _
    
    /**
     * Adds data to our linear regression model
     */
    def addData(data: Array[Array[Double]], labels: Array[Double]) {
        currentData ++= data
        currentLabels ++= labels
        regression.newSampleData(currentLabels, currentData)
    }
    
    /**
     * Predicts a Y-value for X-values
     */
    def classify(data: Seq[Double]) = {
        val weights = regression.estimateRegressionParameters()
        data.zip(weights.drop(0)).foldLeft(weights(0))((s, z) => s + z._1 * z._2)
    }
    
    override def serialize(filename: String) = {
        // Write out model
        val oos = new ObjectOutputStream(new FileOutputStream(filename))
        oos.writeObject(regression)
        oos.close
    }
    
    override def deserialize(filename: String) = {
        val ois = new ObjectInputStream(new FileInputStream(filename))
        regression = ois.readObject.asInstanceOf[OLSMultipleLinearRegression]
        ois.close
    }
}