package tuktu.nlp.models

import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache

import fasttext.Args
import fasttext.FastText
import fasttext.Vector
import play.api.Play
import tuktu.ml.models.BaseModel


class FastTextWrapper(lr: Double, lrUpdateRate: Int, dim: Int, ws: Int, epoch: Int, minCount: Int, minCountLabel: Int,
        neg: Int, wordNgrams: Int, lossName: String, modelName: String, bucket: Int, minn: Int, maxn: Int, thread: Int, t: Double,
        label: String, pretrainedVectors: String) extends BaseModel {
    // Set up fastText instance to use throughout this class
    val fasttext = new FastText()
    // Set up arguments
    val args = new Args
	args.lr = lr
	args.lrUpdateRate = lrUpdateRate
	args.dim = dim
	args.ws = ws
	args.epoch = epoch
	args.minCount = minCount
	args.minCountLabel = minCountLabel
	args.neg = neg
	args.wordNgrams = wordNgrams
	args.loss = lossName match {
        case "hs" => Args.loss_name.hs
        case "softmax" => Args.loss_name.softmax
        case _ => Args.loss_name.ns
    }
    args.model = modelName match {
        case "sup" => Args.model_name.sup
        case "cbow" => Args.model_name.cbow
        case _ => Args.model_name.sg
    }
	args.bucket = bucket
	args.minn = minn
	args.maxn = maxn
	args.thread = thread
	args.t = t
	args.label = label
	args.verbose = 0
    args.pretrainedVectors = pretrainedVectors
    fasttext.setArgs(args)
    
    def getArgs() = fasttext.getArgs
    
    def predict(tokens: Seq[String]) =
		fasttext.predict(tokens.toArray, 1).toList.map {p =>
		    (p.getValue, p.getKey)
		}.head
		
	def getSentenceVector(tokens: Seq[String]) =
	    fasttext.sentenceVectors(tokens.toArray).data_.map(_.toDouble)
	    
	def getWordVector(word: String) = {
		var v = new Vector(fasttext.getArgs.dim)
	    fasttext.getVector(v, word)
	    v.data_.map(_.toDouble)
    }
		
	override def serialize(filename: String) = {
		val saveArgs = fasttext.getArgs
		saveArgs.output = filename
		fasttext.setArgs(saveArgs)
        fasttext.saveModel
    }
	
	/**
     * This classifier is similar to the one above but instead of looking at averaged word vectors, it looks at vectors word-by-word
     * and sees if there is a close-enough overlap between one or more candidate set words and the sentence's words.
     */
    def simpleWordOverlapClassifier(inputWords: List[String], candidateWordsClasses: Seq[Seq[Array[Double]]], cutoff: Double = 0.225) = {
        // Conver input words
        val vectors = inputWords.map(getWordVector)
        // Go over the candidate sets and match
        candidateWordsClasses.zipWithIndex.map {wordClass =>
            // Average of all the word similarities
            val similarities = for {
                vector <- vectors
                word <- wordClass._1
                similarity = CosineSimilarity.cosineSimilarity(vector, word)
                if (similarity >= cutoff)
            } yield similarity
            
            // See if we found any
            if (similarities.isEmpty) (wordClass._2, 0.0) else (wordClass._2, similarities.sum / similarities.size.toDouble)
        } sortWith((a,b) => a._2 > b._2)
    }
		
    override def deserialize(filename: String) = fasttext.loadModel(filename)
}

object FastTextCache {
    // Keep an eviction cache of models to make sure we don't load too many into memory at once
    val models: LoadingCache[String, FastTextWrapper] = CacheBuilder.newBuilder()
        .maximumSize(Play.current.configuration.getLong("tuktu.nlp.fasttext.max_cache_size").getOrElse(4L))
        .expireAfterAccess(Play.current.configuration.getLong("tuktu.nlp.fasttext.expiration_duration").getOrElse(10L), TimeUnit.MINUTES)
        .build(
            new CacheLoader[String, FastTextWrapper]() {
                def load(modelName: String) = {
                    val model = new FastTextWrapper(0.05, 100 , 100, 5, 5, 5, 0, 5, 1, "ns", "sg", 2000000, 3, 6 , {
                        Runtime.getRuntime().availableProcessors /
                        Play.current.configuration.getLong("tuktu.nlp.fasttext.max_cache_size").getOrElse(4L).toInt
                    }, 1e-4, "__label__", "")
                    model.deserialize(modelName)
                    model
                }
            }
        )
        
    def getModel(modelName: String) = models.get(modelName)
}