package tuktu.nlp.models

import tuktu.ml.models.BaseModel
import nl.et4it.Tokenizer
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.FileInputStream
import java.io.ObjectInputStream

class TFIDF() extends BaseModel {
    // Keep track of word counts
    var wordCounts = collection.mutable.Map.empty[String, Int]
    var docCounts = 0
    
    /**
     * Adds documents to the word counts
     */
    def addDocument(document: String): Unit = addDocument(Tokenizer.tokenize(document) toList)
    
    def addDocument(tokens: List[String]): Unit = {
        // Add all distinct tokens
        tokens.distinct.foreach(token => {
            if (!wordCounts.contains(token)) wordCounts += token -> 0
            wordCounts(token) += 1
        })
        docCounts += 1
    }
    
    /**
     * Computes TF-IDF scores
     */
    def computeScores(document: String): Map[String, Double] = computeScores(Tokenizer.tokenize(document) toList)
    
    def computeScores(tokens: List[String]): Map[String, Double] = {
        val tokensByCount = tokens.groupBy(t => t).map(t => t._1 -> t._2.size)
        
        for ((token, count) <- tokensByCount) yield {
            token -> (count.toDouble * Math.log(
                    (1 + docCounts) / // Total document counts
                    1 + {if (wordCounts.contains(token)) wordCounts(token) else 0} // Occurrences in documents
            ))
        }
    }
    
    override def serialize(filename: String): Unit = {
        // Write out word counts
        val oos = new ObjectOutputStream(new FileOutputStream(filename))
        oos.writeObject(Map(
                "w" -> wordCounts,
                "d" -> docCounts
        ))
        oos.close
    }
    
    override def deserialize(filename: String): Unit = {
        // Load word counts
        val ois = new ObjectInputStream(new FileInputStream(filename))
        val obj = ois.readObject.asInstanceOf[Map[String, Any]]
        ois.close
        
        // Set back weights
        wordCounts = obj("w").asInstanceOf[collection.mutable.Map[String, Int]]
        docCounts = obj("d").asInstanceOf[Int]
    }
}