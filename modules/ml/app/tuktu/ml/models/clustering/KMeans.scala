package tuktu.ml.models.clustering

import tuktu.ml.models.BaseModel
import tuktu.ml.models.smileio.Operators
import smile.clustering

class KMeans extends BaseModel with Operators {
    var model: clustering.KMeans = _
    
    def cluster(data: Array[Array[Double]], k: Int, maxIter: Option[Int], runs: Option[Int]) = {
        model = maxIter match {
            case Some(mi) => runs match {
                case Some(r) => new clustering.KMeans(data, k, mi, r)
                case None => new clustering.KMeans(data, k, mi)
            }
            case None => new clustering.KMeans(data, k)
        }
    }
    
    def predict(data: Array[Double]) =
        model.predict(data)
        
    override def serialize(filename: String) = write(model, filename)
    
    override def deserialize(filename: String) = { model = read(filename).asInstanceOf[clustering.KMeans] }
}