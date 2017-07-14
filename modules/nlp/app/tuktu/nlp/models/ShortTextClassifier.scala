package tuktu.nlp.models

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import tuktu.ml.models.BaseModel

import de.bwaldvogel.liblinear._
import java.io.File

class ShortTextClassifier(n: Int, minCount: Double) extends BaseModel {
    // Map containing the terms that we have found so far amd their feature indexes
    val featureMap = collection.mutable.Map.empty[String, (Int, Int)]
    var featureOffset = 1
    var _n: Int = n
    var _minCount: Double = minCount
    var model: Model = _
    
    def addDocument(tokens: List[String]) = {
        // Construct the N-grams
        val ngrams = (1 to _n).toList.foldLeft(List.empty[Seq[String]])((a,b) => {
            a ++ NLP.getNgrams(tokens, b)
        })
        // Add to the map
        ngrams.foreach {ngram =>
            val ng = ngram.mkString
            if (!featureMap.contains(ng)) {
                featureMap += ng -> (featureOffset, 0)
                featureOffset += 1
            }
            featureMap += ng -> (featureMap(ng)._1, featureMap(ng)._2 + 1)
        }
    }
    
    def tokensToVector(tokens: List[String]): Array[Feature] = {
        val ngrams = (1 to _n).toList.foldLeft(List.empty[Seq[String]])((a,b) => {
            a ++ NLP.getNgrams(tokens, b)
        }).groupBy(a => a).map(a => a._1.mkString -> a._2.size)
            
        (ngrams.filter(w => featureMap.contains(w._1)).map {token =>
            new FeatureNode(featureMap(token._1)._1, token._2)
        } toList).sortBy {
            _.getIndex
        } toArray
    }
        
    def trainClassifier(data: List[List[String]], labels: List[Double], C: Double, eps: Double, language: String) = {
        // Get all sentences
        val sentences = data.flatMap(d => NLP.getSentences(d, language)).map(_.split(" ").toList)
        // Add all data
        sentences.foreach(addDocument)
        
        // Get the minCount percent of most frequent features
        {
            val newFeatures = featureMap.toList.sortBy(_._2._2)(Ordering[Int].reverse)
                .take(Math.floor(featureMap.size.toDouble * _minCount).toInt)
            featureMap.clear
            featureMap ++= newFeatures
        }
        /*_minCount = minCount * featureMap.size.toDouble
        // Remove all words occurring too infrequently
        featureMap.retain((k,v) => v._2 >= _minCount)*/
        // Renumber them all
        featureOffset = 1
        featureMap.foreach {fm =>
            featureMap.update(fm._1, (featureOffset, fm._2._2))
            featureOffset += 1
        }
        
        // Set up the data
        val p = new Problem
        p.n = featureMap.size
        // Construct the liblinear vectors now
        p.x = sentences.map {datum =>
            tokensToVector(datum)
        } filter {
            !_.isEmpty
        } toArray
        
        p.l = p.x.size
        p.y = labels.toArray
        
        val param = new Parameter(SolverType.MCSVM_CS, C, eps)
        // Train model
        model = Linear.train(p, param)
    }
    
    def predict(tokens: List[String], language: String) = {
        // Get sentences
        val sentences = NLP.getSentences(tokens, language)
        if (sentences.isEmpty) -1.0 else
            (sentences.map {sentence =>
                val vector = tokensToVector(sentence.split(" ").toList)
                if (vector.isEmpty) -1.0 else Linear.predict(model, vector)
            }).groupBy(a => a).map(pred => {
                pred._1 -> pred._2.size
            }).toList.sortBy(_._2)(Ordering[Int].reverse).head._1
    }
    
    override def serialize(filename: String): Unit = {
        val oos = new ObjectOutputStream(new FileOutputStream(filename))
        oos.writeObject(Map(
                "f" -> featureMap,
                "n" -> _n,
                "m" -> _minCount
        ))
        oos.close
        model.save(new File(filename + ".svm"))
    }

    override def deserialize(filename: String): Unit = {
        val ois = new ObjectInputStream(new FileInputStream(filename))
        val obj = ois.readObject.asInstanceOf[Map[String, Any]]
        ois.close

        featureMap.clear
        featureMap ++= obj("f").asInstanceOf[collection.mutable.Map[String, (Int, Int)]]
        _n = obj("n").asInstanceOf[Int]
        _minCount = obj("m").asInstanceOf[Double]
        
        model = Model.load(new File(filename + ".svm"))
    }
}