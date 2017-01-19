package tuktu.nlp.models

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.zip.GZIPInputStream

import scala.collection.mutable.HashMap
import scala.collection.mutable.MutableList
import scala.collection.mutable.PriorityQueue
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.apache.commons.compress.compressors.gzip.GzipUtils
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.ops.transforms.Transforms

import scala.collection.JavaConversions._

import tuktu.ml.models.BaseModel

class Word2Vec() extends BaseModel {
    // Keep track of word counts
    var wordMap: HashMap[String, INDArray] = new HashMap[String, INDArray]()
    // size of the input layers
    var layerSize = 300
    
    /**
     * Gets a document vector by simply taking the mean of word vectors
     */
    def getAverageDocVector(inputWords: Seq[String]) = {
        val containedWords = inputWords.filter(wordMap.contains(_))
        if (containedWords.size != 0) {
            val allWords = Nd4j.create(containedWords.size, layerSize)
    
            containedWords.zipWithIndex.foreach{wi =>
                val (word, index) = (wi._1, wi._2)
                allWords.putRow(index, wordMap(word))
            }
    
            allWords.mean(0)
        } else Nd4j.create((0 to layerSize - 1).map(_ => 0.0).toArray)
    }

    /**
     * Calculate top nearest words to the given list of words.
     *
     * @param inputWords The word to find nearest words to
     * @param top How many words should be returned
     */
    def wordsNearest(inputWords: Seq[String], top: Integer): Map[String, Double] = {
        // get rid of words not available in wordMap
        val clean = inputWords.filter(wordMap.contains(_))
        if (clean.isEmpty)
            Map.empty
        else {
            // container for all the arrays
            val words: INDArray = Nd4j.create(clean.size, layerSize)
            // add words to the container
            for ((word, row) <- clean.zipWithIndex)
                words.putRow(row, wordMap(word))
            // a queue with all the distances to the given words
            val distances = new PriorityQueue[(String, Double)]()(Ordering.by[(String, Double), Double](_._2))
            // calculate center of given words
            val mean = {
                if (words.isMatrix) words.mean(0)
                else words
            }
            // calculate distances
            val futures: Seq[Future[(String, Double)]] = (wordMap.map {
                case (word, vector) => {
                    Future {
                        (word, Transforms.cosineSim(mean, vector))
                    }
                }
            }).toSeq
            // aggregate all the futures
            val aggregated = Future.sequence(futures)
            // wait a long time for it to be finished
            val sims = Await.result(aggregated, 15.minutes)
            // add all to the queue
            sims.foreach(f => distances.enqueue(f._1 -> f._2))

            // remove words which were in the input
            var result = MutableList[(String, Double)]()
            while (result.size < top && distances.nonEmpty) {
                val w = distances.dequeue
                if (!clean.contains(w._1))
                    result += w
            }
            // return result
            result.toMap
        }
    }

    override def serialize(filename: String): Unit = {
        // Write out word counts
        val oos = new ObjectOutputStream(new FileOutputStream(filename))
        oos.writeObject(Map(
            "wordMap" -> wordMap,
            "layerSize" -> layerSize))
        oos.close
    }

    override def deserialize(filename: String): Unit = {
        // Load word counts
        val ois = new ObjectInputStream(new FileInputStream(filename))
        val obj = ois.readObject.asInstanceOf[Map[String, Any]]
        ois.close

        // Set back weights
        wordMap = obj("wordMap").asInstanceOf[HashMap[String, INDArray]]
        layerSize = obj("layerSize").asInstanceOf[Int]
    }

    /**
     * Load a word2vec file in the following format:
     * word [vector]
     * @param file Location of the file
     */
    def loadTextModel(file: String) = {
        wordMap.clear
        try {
            val modelFile = new File(file)
            val br = new BufferedReader(new InputStreamReader(
                {
                    if (GzipUtils.isCompressedFilename(modelFile.getName()))
                        new GZIPInputStream(new FileInputStream(modelFile))
                    else
                        new FileInputStream(modelFile)
                }))

            Stream.continually(br.readLine()).takeWhile(_ != null).foreach { line =>
                {
                    val split = line.split(" ").toList
                    val word = split(0)
                    layerSize = split.length - 1
                    val vector = split.drop(1).map(java.lang.Float.parseFloat).toArray

                    wordMap += word -> Nd4j.create(vector)
                }
            }

            // clean up
            br.close
        } catch {
            case e: Exception => e.printStackTrace
        }
    }

    /**
     * Load a binary Word2Vec file.
     * @param file Location of the file.
     */
    def loadBinaryModel(file: String) {
        wordMap.clear
        try {
            val modelFile = new File(file)
            val bis = new BufferedInputStream(
                {
                    if (GzipUtils.isCompressedFilename(modelFile.getName()))
                        new GZIPInputStream(new FileInputStream(modelFile))
                    else
                        new FileInputStream(modelFile)
                })

            val dis = new DataInputStream(bis)
            val words = Integer.parseInt(readString(dis))
            layerSize = Integer.parseInt(readString(dis))
            for (i <- 0 to words - 1) {
                val word = readString(dis)
                if (i % 100000 == 0)
                    println("Loading " + word + " with word " + i)

                val vector = (for (j <- 0 to layerSize - 1) yield {
                    readFloat(dis)
                }).toArray

                wordMap += word -> Nd4j.create(vector)
            }

            // clean up
            dis.close
            bis.close

        } catch {
            case e: Exception => e.printStackTrace
        }

    }

    val MAX_SIZE = 50

    /**
     * Read a float from a data input stream Credit to:
     * https://github.com/NLPchina/Word2VEC_java/blob/master/src/com/ansj/vec/Word2VEC.java
     *
     * @param is
     * @return
     * @throws IOException
     */
    def readFloat(is: InputStream) = {
        var bytes: Array[Byte] = new Array[Byte](4)
        is.read(bytes)
        getFloat(bytes)
    }

    /**
     * Read a string from a data input stream Credit to:
     * https://github.com/NLPchina/Word2VEC_java/blob/master/src/com/ansj/vec/Word2VEC.java
     *
     * @param b
     * @return
     * @throws IOException
     */
    def getFloat(b: Array[Byte]) = {
        var accum: Integer = 0
        accum = accum | (b(0) & 0xff) << 0
        accum = accum | (b(1) & 0xff) << 8
        accum = accum | (b(2) & 0xff) << 16
        accum = accum | (b(3) & 0xff) << 24
        java.lang.Float.intBitsToFloat(accum)
    }

    /**
     * Read a string from a data input stream Credit to:
     * https://github.com/NLPchina/Word2VEC_java/blob/master/src/com/ansj/vec/Word2VEC.java
     *
     * @param dis
     * @return
     * @throws IOException
     */
    def readString(dis: DataInputStream) = {
        var bytes: Array[Byte] = new Array[Byte](MAX_SIZE)
        var b = dis.readByte
        var i = -1;
        val sb = new StringBuilder
        while (b != 32 && b != 10) {
            i += 1
            bytes(i) = b
            b = dis.readByte
            if (i == 49) {
                sb.append(new String(bytes))
                i = -1;
                bytes = new Array[Byte](MAX_SIZE)
            }
        }
        sb.append(new String(bytes, 0, i + 1));
        sb.toString
    }
}