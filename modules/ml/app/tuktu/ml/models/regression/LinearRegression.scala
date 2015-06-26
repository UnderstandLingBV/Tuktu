package tuktu.ml.models.regression

import tuktu.ml.models.BaseModel
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class LinearRegression extends BaseModel {
    val regression = new OLSMultipleLinearRegression
    
    def classify(x: Seq[Double]) = {
        val weights = regression.estimateRegressionParameters()
        x.zip(weights.drop(0)).foldLeft(weights(0))((s, z) => s + z._1 * z._2)
    }
    
    override def serialize(filename: String) = {
        // Write out model
        val oos = new ObjectOutputStream(new FileOutputStream(filename))
        oos.writeObject(regression)
        oos.close
    }
    
    override def deserialize(filename: String) = {
        val ois = new ObjectInputStream(new FileInputStream(filename))
        val obj = ois.readObject.asInstanceOf[OLSMultipleLinearRegression]
        ois.close
    }
}