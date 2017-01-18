package tuktu.nlp.models

import tuktu.ml.models.BaseModel
import nl.et4it.Tokenizer
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.FileInputStream
import java.io.ObjectInputStream

class TFIDF() extends BaseModel {
    // Keep track of word counts
    var wordCounts = collection.mutable.Map.empty[String, collection.mutable.Map[String, Int]]
    var docCounts = collection.mutable.Map.empty[String, Int]

    /**
     * Adds documents to the word counts
     */
    def addDocument(document: String, label: Option[String]): Unit = addDocument(Tokenizer.tokenize(document) toList, label)

    def addDocument(tokens: List[String], label: Option[String]): Unit = {
        // Increment all distinct token counts and document count
        for (token <- tokens.distinct) {
            if (!wordCounts.contains(token))
                wordCounts += token -> collection.mutable.Map.empty[String, Int]
                
            label match {
                case Some(lbl) => {
                    if (!wordCounts.contains(token)) wordCounts += token -> collection.mutable.Map.empty[String, Int]
                    if (!wordCounts(token).contains(lbl)) wordCounts(token) += lbl -> 0
                    wordCounts(token)(lbl) += 1
                }
                case None => {
                    if (!wordCounts.contains(token)) wordCounts += token -> collection.mutable.Map.empty[String, Int]
                    if (!wordCounts(token).contains("")) wordCounts(token) += "" -> 0
                    wordCounts(token)("") += 1
                }
            }
        }
        
        label match {
            case Some(lbl) => {
                if (!docCounts.contains(lbl)) docCounts += lbl -> 0
                docCounts(lbl) += 1
            }
            case None => {
                if (!docCounts.contains("")) docCounts += "" -> 0
                docCounts("") += 1
            }
        }
    }

    /**
     * Computes TF-IDF scores
     */
    def computeScores(document: String): Map[String, Double] = computeScores(Tokenizer.tokenize(document) toList)

    def computeScores(tokens: List[String]): Map[String, Double] = {
        val tokensByCount = tokens.groupBy(t => t).map(t => t._1 -> t._2.size)

        for ((token, count) <- tokensByCount) yield {
            token -> (count * math.log(
                    (1.0 + {
                        // Total document counts
                        if (docCounts.size == 1 && docCounts.head._1 == "") docCounts("").toDouble
                        else docCounts.keySet.size.toDouble
                    }) / (1.0 + {
                        // Occurrences in documents
                        if (!wordCounts.contains(token)) 0.0
                        else wordCounts(token).map(_._2).sum.toDouble
                    }) 
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
        wordCounts = obj("w").asInstanceOf[collection.mutable.Map[String, collection.mutable.Map[String, Int]]]
        docCounts = obj("d").asInstanceOf[collection.mutable.Map[String, Int]]
    }
}