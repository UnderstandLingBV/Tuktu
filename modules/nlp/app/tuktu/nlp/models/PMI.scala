package tuktu.nlp.models

import tuktu.ml.models.BaseModel

object PMI {
    def computePMI(classDocuments: Map[String, List[String]], words: List[String], language: String, retain: Int) = {
        // Compute per class first
        val classPMIs = classDocuments.map {classDoc =>
            val label = classDoc._1
            val documents = classDoc._2
            
            // Get the words of all documents
            val docs = documents.flatMap(doc => NLP.getSentences(doc, language).filter(!_.isEmpty).map(_.split(" ").toArray))
            // Document counts per word
            val wordCounts = docs.flatMap {doc =>
                doc.distinct
            } groupBy {w => w} map {w => w._1 -> w._2.size}
            // Find the ones containing our words
            val containedDocs = docs.filter(!_.intersect(words).isEmpty) 
            // Process for each word
            label -> (words.map {word =>
                val thisWordsDocs = containedDocs.filter(_.contains(word))
                // Get all the words' co-occurrence
                val allWords = thisWordsDocs.flatMap {doc =>
                    doc.distinct
                } groupBy {w => w} map {w =>
                    w._1 -> w._2.size
                }
                // Now compute the PMI of each word and our target word
                word -> (allWords.map {thisWord =>
                    thisWord._1 -> Math.log((thisWord._2.toDouble / thisWordsDocs.size) / wordCounts(thisWord._1).toDouble)
                } toList).sortBy(_._2)(Ordering[Double].reverse)
            } toMap)
        }
        
        // Now subtract the other class' PMIs from the current class' PMIs
        classPMIs.map {cp =>
            val label = cp._1
            val pmis = cp._2
            
            // Get the PMIs of all other classes
            val otherClassesPMIs = classPMIs.toList.filter(_._1 != label).flatMap(_._2.flatMap(_._2))
            
            // Go over this class' words
            label -> pmis.map {pmi =>
                pmi._1 -> (pmi._2.map {wordScore =>
                    // Find the other classes' max PMI for this word
                    val otherScores = otherClassesPMIs.filter(_._1 == wordScore._1).sortBy(_._2)
                    if (otherScores.size > 0)
                        (wordScore._1, wordScore._2 - otherScores.foldLeft(0.0)(_ + _._2))
                    else wordScore
                }).sortBy(_._2)(Ordering[Double].reverse).take(retain)
            }
        }
    }
}