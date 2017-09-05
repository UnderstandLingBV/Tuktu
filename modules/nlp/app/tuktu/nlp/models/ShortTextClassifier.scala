package tuktu.nlp.models

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import tuktu.ml.models.BaseModel

import de.bwaldvogel.liblinear._
import java.io.File
import com.vdurmont.emoji.EmojiParser

class ShortTextClassifier(
        minCount: Int
) extends BaseModel {
    // Map containing the terms that we have found so far amd their feature indexes
    val featureMap = collection.mutable.Map.empty[String, (Int, Int)]
    var additionalFeaturesNum: Int = _
    var featureOffset = 1
    var _minCount: Int = minCount
    var _seedWords: Map[String, List[String]] = _
    var _rightFlips: List[String] = _
    var _leftFlips: List[String] = _
    var model: Model = _
    var splitSentences: Boolean = _

    def setWords(seedWords: Map[String, List[String]], rightFlips: List[String], leftFlips: List[String], splitSentences: Boolean = true) {
        _seedWords = seedWords
        _rightFlips = rightFlips
        _leftFlips = leftFlips
        this.splitSentences = splitSentences
    }

    def processTokens(tokens: List[String]) = {
        // Convert the negated tokens
        val processedTokens = collection.mutable.ArrayBuffer.empty[String]
        val seedIndices = collection.mutable.ArrayBuffer.empty[Int]
        processedTokens ++= tokens.zipWithIndex.map { case (token, index) =>
            _seedWords.find { case (_, sw) =>
                sw.contains(token)
            } match {
                case Some((label, _)) =>
                    // Replace token by the label of this word
                    seedIndices.append(index)
                    label
                case None => token
            }
        }
        tokens.zipWithIndex.map { case (token, index) =>
            if (_rightFlips.contains(token))
                // Negate words to the right
                (1 to 2).foreach { offset =>
                    if (processedTokens.size > index + offset && seedIndices.contains(index + offset))
                        if (processedTokens(index + offset).endsWith("_NEG"))
                            processedTokens(index + offset) = processedTokens(index + offset).take(processedTokens(index + offset).size - 4)
                        else processedTokens(index + offset) = processedTokens(index + offset) + "_NEG"
                }
            else if (_leftFlips.contains(token))
                // Negate words to the left
                (1 to 2).foreach { offset =>
                    if (index - offset >= 0 && seedIndices.contains(index + offset))
                        if (processedTokens(index - offset).endsWith("_NEG"))
                            processedTokens(index - offset) = processedTokens(index - offset).take(processedTokens(index - offset).size - 4)
                        else processedTokens(index - offset) = processedTokens(index - offset) + "_NEG"
                }
        }
        processedTokens.toList
    }

    def getNgramFeatures(tokens: List[String], processedTokens: List[String]): List[String] = {
        // Construct the word N-grams
        (1 to 3).foldLeft(List.empty[String])((acc, b) => {
            acc ++ NLP.getNgrams(processedTokens.toList, b).map(_.mkString)
        }) ++
            // Construct the character N-grams
            (3 to 5).foldLeft(List.empty[String])((acc, b) => {
                acc ++ NLP.getNgramsChar(tokens.mkString(" ").toList, b).map(_.mkString)
            })
    }

    def addDocument(tokens: List[String], processedTokens: List[String]) {
        // Construct the word N-grams
        val ngrams = getNgramFeatures(tokens, processedTokens)

        // Add to the map
        ngrams.foreach { ng =>
            if (!featureMap.contains(ng)) {
                featureMap += ng -> (featureOffset, 0)
                featureOffset += 1
            }
            featureMap += ng -> (featureMap(ng)._1, featureMap(ng)._2 + 1)
        }
    }

    def getStaticFeatures(tokens: List[String]) = {
        val sentence = tokens.mkString(" ")

        // Get punctuation
        val punctuation = sentence.count { char =>
            List("!", ".", ",", "?", "!", ":", ";", "'", "\"", "[", "]", "{", "}", "(", ")", "-", "+", "=", "&", "%", "$", "€").exists(_ == char)
        }.toDouble / sentence.size.toDouble

        // Caps usage
        val caps = sentence.count { char =>
            char.isUpper
        }.toDouble / sentence.size.toDouble

        // Number of vowels
        val vowels = sentence.count { char =>
            List("a", "e", "o", "i", "u", "y",
                  "ä", "á", "à", "â", "ã",
                  "ë", "é", "è", "ê",
                  "ö", "ó", "ò", "ô", "õ",
                  "ï", "í", "ì", "î",
                  "ü", "ú", "ù", "û",
                  "ÿ", "ý"
            ).exists(_ == char)
        }.toDouble / sentence.size.toDouble

        // Words starting with a capital
        val capWords = tokens.count { t => t.nonEmpty && t.head.isUpper }.toDouble / tokens.size.toDouble
        // Slow-release case
        val slowRelease = tokens.count { t =>
            if (t.size > 2) t(0).isUpper && t(1).isUpper else false
        }.toDouble / tokens.size.toDouble

        // First word capital or not?
        val firstWordCap = if (sentence.headOption.map { _.isUpper }.getOrElse(false)) 1.0 else 0.0

        // Count emojis
        val emojis = sentence.size.toDouble - EmojiParser.removeAllEmojis(sentence).size.toDouble

        // How lengthy is this sentence
        val shortLength = if (sentence.size <= 10) 1.0 else 0.0
        val midLength = if (sentence.size > 10 && sentence.size <= 80) 1.0 else 0.0
        val longLength = if (sentence.size > 80) 1.0 else 0.0

        List(punctuation, caps, vowels, capWords, slowRelease, firstWordCap, emojis, shortLength, midLength, longLength)
    }

    def tokensToVector(tokens: List[String], pTokens: Option[List[String]] = None): Array[Feature] = {
        val sentenceSize = tokens.mkString(" ").size.toDouble

        val processedTokens = pTokens match {
            case Some(pt) => pt
            case None => processTokens(tokens)
        }
        val ngrams = getNgramFeatures(tokens, processedTokens).groupBy(w => w).map(w => w._1 -> w._2.size)

        // First 2 features are always static
        val statics = getStaticFeatures(tokens).zipWithIndex.map {feat =>
            new FeatureNode(feat._2 + additionalFeaturesNum, feat._1.toDouble)
        } toArray

        val other = (ngrams.filter(w => featureMap.contains(w._1)).map {token =>
            new FeatureNode(featureMap(token._1)._1, token._2 / sentenceSize)
        } toList).sortBy {
            _.getIndex
        } toArray

        statics ++ other
    }

    def trainClassifier(data: List[List[String]], additionalFeatures: List[Array[FeatureNode]],
            labels: List[Double], C: Double, eps: Double, language: String) = {
        // Get all sentences
        val sentences = data.zipWithIndex.flatMap {d =>
            if (splitSentences)
                NLP.getSentences(d._1, language).filter(!_.isEmpty).map(s => (s, labels(d._2)))
            else
                List((d._1.mkString(" "), labels(d._2)))
        } map {s =>
            (s._1.split(" ").toList, s._2)
        }
        // Add all data
        sentences.foreach {s =>
            // Convert the negated tokens
            val processedTokens = processTokens(s._1)
            addDocument(s._1, processedTokens)
        }

        // Remove all words occurring too infrequently
        featureMap.retain((k,v) => v._2 >= _minCount)

        // Find the highest number of additional features
        additionalFeaturesNum = if (additionalFeatures.size > 0)
            (additionalFeatures.flatten.maxBy {f =>
                f.getIndex
            } getIndex) + 1
        else 1

        // Renumber them all, start at 11 because we have 10 static features
        featureOffset = additionalFeaturesNum + 11
        featureMap.foreach {fm =>
            featureMap.update(fm._1, (featureOffset, fm._2._2))
            featureOffset += 1
        }

        // Set up the data
        val p = new Problem
        // Add the ten features we always add to n, also add the featuresToAdd
        p.n = featureMap.size + 10 + additionalFeaturesNum
        // Construct the liblinear vectors now
        p.x = if (additionalFeatures.size > 0) {
            sentences.zip(additionalFeatures).map {datum =>
                datum._2 ++ tokensToVector(datum._1._1)
            } toArray
        }
        else sentences.map {datum =>
            tokensToVector(datum._1)
        } toArray

        p.l = p.x.size
        p.y = sentences.map(_._2).toArray

        val param = new Parameter(SolverType.MCSVM_CS, C, eps)
        // Train model
        model = Linear.train(p, param)
    }

    def predict(tokens: List[String], additionalFeatures: Array[FeatureNode], language: String, defaultClass: Option[Int] = None): Double = {
        // Get sentences
        val sentences = if (splitSentences) NLP.getSentences(tokens, language) else List(tokens.mkString(" "))
        if (sentences.foldLeft(0)(_ + _.size) < 10)
            defaultClass match {
                case Some(c) => c
                case None    => -1.0
            }
        else
            sentences.map { sentence =>
                // Get feature vector with additional features
                val vector = additionalFeatures ++ tokensToVector(sentence.split(" ").toList)

                if (vector.isEmpty)
                    defaultClass match {
                        case Some(c) => c
                        case None    => -1.0
                    }
                else
                    Linear.predict(model, vector)
            }.groupBy { a => a }.maxBy { case (_, group) => group.size }._1
    }

    override def serialize(filename: String): Unit = {
        val oos = new ObjectOutputStream(new FileOutputStream(filename))
        oos.writeObject(Map(
                "f" -> featureMap,
                "a" -> additionalFeaturesNum,
                "minCount" -> _minCount,
                "seedWords" -> _seedWords,
                "rightFlips" -> _rightFlips,
                "leftFlips" -> _leftFlips,
                "split" -> splitSentences
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
        additionalFeaturesNum = obj("a").asInstanceOf[Int]
        _minCount = obj("minCount").asInstanceOf[Int]
        _seedWords = obj("seedWords").asInstanceOf[Map[String, List[String]]]
        _rightFlips = obj("rightFlips").asInstanceOf[List[String]]
        _leftFlips = obj("leftFlips").asInstanceOf[List[String]]
        splitSentences = try {
            obj("split").asInstanceOf[Boolean]
        } catch {
            case e: Exception => true
        }

        model = Model.load(new File(filename + ".svm"))
    }
}