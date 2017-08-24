package tuktu.ml.models.regression

import tuktu.ml.models.BaseModel
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import play.api.libs.json.Json
import scala.io.Source

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
    
    def calculateEstimation(x: Double, coe: Array[Double]) =
        (0 to coe.length - 1).foldLeft(0.0)((a,b) => a + coe(b) * Math.pow(x,b))

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
        val bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), "utf8"))
        bw.write(Json.toJson(coefficients).toString)
        bw.close
    }
    
    override def deserialize(filename: String) = {
        val br = Source.fromFile(filename)("utf8").bufferedReader
        coefficients = Json.parse(br.readLine).as[Array[Double]]
        br.close
    }
}