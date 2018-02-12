package tuktu.nlp.models

import tuktu.ml.models.BaseModel
import xl.nbsvm.Classifier
import java.io.File
import org.sgdtk.LogLoss
import xl.nbsvm.Instance
import scala.collection.JavaConversions._

class NBSVM() extends BaseModel {
    val model = new Classifier(
        24, 3, 3, 0.95, false, false, false
    )
    
    def train(epochs: Int, lambda: Double, alpha: Double, texts: List[List[String]], labels: List[Int]) {
        val (iter, iter2) = (labels.zip(texts).map {lt =>
            val i = new Instance
            i.label = lt._1
            i.text = lt._2
            i
        } toIterator) duplicate
        
        model.fit(
            new LogLoss, epochs, 16384, lambda, 0.5, iter, iter2, alpha, false, false, new File("nbsvmcache")
        )
    }
    
    def apply(text: List[String]) = {
        val i = new Instance
        i.text = text
        val x = model.classify(i)
        Math.round(model.classify(i)).toInt
    }
    
    override def serialize(filename: String) {
        model.save(filename)
    }
    
    override def deserialize(filename: String) {
        model.load(filename)
    }
}