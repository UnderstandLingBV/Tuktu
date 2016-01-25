package tuktu.ml.models.decisitiontrees

import tuktu.ml.models.BaseModel
import smile.classification
import tuktu.ml.models.smileio.Operators

class DecisionTree extends BaseModel with Operators {
    var model: classification.DecisionTree = _
    
    def train(data: Array[Array[Double]], labels: Array[Int], maxNodes: Int) =
        model = new classification.DecisionTree(data, labels, maxNodes)
    
    def classify(data: Array[Double]) =
        model.predict(data)
        
    override def serialize(filename: String) = write(model, filename)
    
    override def deserialize(filename: String) = { model = read(filename).asInstanceOf[classification.DecisionTree] }
}