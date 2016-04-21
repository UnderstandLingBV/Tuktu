package tuktu.ml.models.regression

import tuktu.ml.models.BaseModel
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.RealVector
import org.apache.commons.math3.linear.SingularValueDecomposition
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import smile.regression
import tuktu.ml.models.smileio.Operators

/**
 * Performs ridge regression
 */
class RidgeRegression(lambda: Double) extends BaseModel with Operators {
    var currentData = Array[Array[Double]]()
	var currentLabels = Array[Double]()
	
	var model: regression.RidgeRegression = null

	def addData(x: Array[Array[Double]], y: Array[Double]) {
	    currentData ++= x
	    currentLabels ++= y
	}
    
    def train() =
        model = new regression.RidgeRegression(currentData, currentLabels, lambda)
	
	/**
     * Predicts a Y-value for X-values
     */
    def classify(data: Seq[Double]) = model.predict(data.toArray)
    
    override def serialize(filename: String) = {
        // Write out model
        train
        write(model, filename)
    }
    
    override def deserialize(filename: String) =
        model = read(filename).asInstanceOf[regression.RidgeRegression]
}