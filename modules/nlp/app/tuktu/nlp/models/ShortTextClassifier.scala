package tuktu.nlp.models

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import tuktu.ml.models.BaseModel

import de.bwaldvogel.liblinear._
import java.io.File

class ShortTextClassifier(
        minCount: Int
) extends BaseModel {
    // Map containing the terms that we have found so far amd their feature indexes
    val featureMap = collection.mutable.Map.empty[String, (Int, Int)]
    var featureOffset = 1
    var _minCount: Int = minCount
    var _seedWords: Map[String, List[String]] = _
    var _rightFlips: List[String] = _    
    var _leftFlips: List[String] = _
    var model: Model = _
    
    def setWords(seedWords: Map[String, List[String]], rightFlips: List[String], leftFlips: List[String]) {
        _seedWords = seedWords
        _rightFlips = rightFlips
        _leftFlips = leftFlips
    }
    
    def processTokens(tokens: List[String]) = {
        // Convert the negated tokens
        val processedTokens = collection.mutable.ArrayBuffer.empty[String]
        val seedIndices = collection.mutable.ArrayBuffer.empty[Int]
        processedTokens ++= tokens.zipWithIndex.map {token =>
            _seedWords.find {sw =>
                sw._2.contains(token._1)
            } match {
                case Some(sw) => {
                    // Replace token by the label of this word
                    seedIndices += token._2
                    sw._1
                }
                case None => token._1
            }
        }
        tokens.zipWithIndex.map {token =>
            if (_rightFlips.contains(token._1))
                // Negate words to the right
                (1 to 2).foreach {offset =>
                    if (processedTokens.size > token._2 + offset && seedIndices.contains(token._2 + offset))
                        if (processedTokens(token._2 + offset).endsWith("_NEG"))
                            processedTokens(token._2 + offset) = processedTokens(token._2 + offset).take(processedTokens(token._2 + offset).size - 4)
                        else processedTokens(token._2 + offset) = processedTokens(token._2 + offset) + "_NEG"
                }
            else if (_leftFlips.contains(token._1))
                // Negate words to the left
                (1 to 2).foreach {offset =>
                    if (token._2 - offset >= 0 && seedIndices.contains(token._2 + offset))
                        if (processedTokens(token._2 - offset).endsWith("_NEG"))
                            processedTokens(token._2 - offset) = processedTokens(token._2 - offset).take(processedTokens(token._2 - offset).size - 4)
                        else processedTokens(token._2 - offset) = processedTokens(token._2 - offset) + "_NEG"
                }
        }
        processedTokens.toList
    }
    
    def getNgramFeatures(tokens: List[String], processedTokens: List[String]) = {
        // Construct the word N-grams
        (1 to 3).toList.foldLeft(List.empty[String])((a,b) => {
            a ++ NLP.getNgrams(processedTokens.toList, b).map(_.mkString)
        }) ++
            // Construct the character N-grams
            (3 to 5).toList.foldLeft(List.empty[String])((a,b) => {
                a ++ NLP.getNgramsChar(tokens.mkString(" ").toList, b).map(_.toString)
            })
    }
    
    def addDocument(tokens: List[String]) = {
        // Convert the negated tokens
        val processedTokens = processTokens(tokens)
        
        // Construct the word N-grams
        val ngrams = getNgramFeatures(tokens, processedTokens)
        
        // Add to the map
        ngrams.foreach {ng =>
            if (!featureMap.contains(ng)) {
                featureMap += ng -> (featureOffset, 0)
                featureOffset += 1
            }
            featureMap += ng -> (featureMap(ng)._1, featureMap(ng)._2 + 1)
        }
    }
    
    def tokensToVector(tokens: List[String]): Array[Feature] = {
        val processedTokens = processTokens(tokens)
        val ngrams = getNgramFeatures(tokens, processedTokens).groupBy(w => w).map(w => w._1 -> w._2.size)
            
        (ngrams.filter(w => featureMap.contains(w._1)).map {token =>
            new FeatureNode(featureMap(token._1)._1, token._2)
        } toList).sortBy {
            _.getIndex
        } toArray
    }
        
    def trainClassifier(data: List[List[String]], labels: List[Double], C: Double, eps: Double, language: String) = {
        // Get all sentences
        val sentences = data.zipWithIndex.flatMap {d =>
            NLP.getSentences(d._1, language).filter(!_.isEmpty).map(s => (s, labels(d._2)))
        } map {s =>
            (s._1.split(" ").toList, s._2)
        }
        // Add all data
        sentences.foreach(s => addDocument(s._1))

        // Remove all words occurring too infrequently
        featureMap.retain((k,v) => v._2 >= _minCount)
        
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
            tokensToVector(datum._1)
        } toArray
        
        p.l = p.x.size
        p.y = sentences.map(_._2).toArray
        
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
                "minCount" -> _minCount,
                "seedWords" -> _seedWords,
                "rightFlips" -> _rightFlips,
                "leftFlips" -> _leftFlips
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
        _minCount = obj("minCount").asInstanceOf[Int]
        _seedWords = obj("seedWords").asInstanceOf[Map[String, List[String]]]
        _rightFlips = obj("rightFlips").asInstanceOf[List[String]]
        _leftFlips = obj("leftFlips").asInstanceOf[List[String]]
        
        model = Model.load(new File(filename + ".svm"))
    }
}