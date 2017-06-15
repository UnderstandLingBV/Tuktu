package tuktu.ml.models.decisitiontrees

import tuktu.ml.models.BaseModel
import smile.classification
import tuktu.ml.models.smileio.Operators

class GradientTreeBoost extends BaseModel with Operators {
    var model: classification.GradientTreeBoost = _

    def train(data: Array[Array[Double]], labels: Array[Int], numTrees: Int, maxNodes: Int, shrinkage: Double, samplingRate: Double) =
        model = new classification.GradientTreeBoost(data, labels, numTrees, maxNodes, shrinkage, samplingRate)

    def classify(data: Array[Double]) =
        model.predict(data)

    override def serialize(filename: String) = write(model, filename)

    override def deserialize(filename: String) = { model = read(filename).asInstanceOf[classification.GradientTreeBoost] }
}