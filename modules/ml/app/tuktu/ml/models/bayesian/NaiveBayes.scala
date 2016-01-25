package tuktu.ml.models.bayesian

import tuktu.ml.models.BaseModel
import smile.classification
import smile.feature.Bag

class NaiveBayes(classes: List[String], features: Array[String]) extends BaseModel {
    var currentData = Array.empty[Array[Double]]
    var currentLabels = Array.empty[String]
    
    def addData(data: String, label: String): Unit = addData(data.split(" "), label)
    
    def addData(data: Seq[String], label: String): Unit = {
        currentLabels ++= Array(label)
        var bag = new Bag[String](features)
        (0 to data.size - 1).foreach(i => {
            currentData ++= Array(bag.feature(data.toArray))
        })
    }
}