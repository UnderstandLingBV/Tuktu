package tuktu.ml.models.regression

import tuktu.ml.models.BaseModel
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import smile.classification
import tuktu.ml.models.smileio.Operators

/**
 * Implements logistic regression
 */
class LogisticRegression(lambda: Double, tolerance: Double, maxIterations: Int) extends BaseModel with Operators {
    var currentData = Array[Array[Double]]()
	var currentLabels = Array[Int]()
	
	var model: classification.LogisticRegression = null

	def addData(x: Array[Array[Double]], y: Array[Int]) {
	    currentData ++= x
	    currentLabels ++= y
	}
    
    def train() =
        model = new classification.LogisticRegression(currentData, currentLabels, lambda, tolerance, maxIterations)
	
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
        model = read(filename).asInstanceOf[classification.LogisticRegression]
}