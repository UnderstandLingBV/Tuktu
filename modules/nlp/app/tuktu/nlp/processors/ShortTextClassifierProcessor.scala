package tuktu.nlp.processors

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.nlp.models.ShortTextClassifier
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor
import tuktu.api.utils
import play.api.libs.json.Json
import com.github.jfasttext.JFastText

class ShortTextClassifierTrainProcessor(resultName: String) extends BaseMLTrainProcessor[ShortTextClassifier](resultName) {
    var tokensField: String = _
    var labelField: String = _
    var minCount: Int = _
    var C: Double = _
    var eps: Double = _
    var lang: String = _
    var rightFlipFile: String = _
    var leftFlipFile: String = _
    var seedWordFile: String = _
    var vectorFile: String = _
    var similarityThreshold: Double = _
    
    override def initialize(config: JsObject) {
        tokensField = (config \ "data_field").as[String]
        labelField = (config \ "label_field").as[String]
        minCount = (config \ "min_count").as[Int]
        
        C = (config \ "C").as[Double]
        eps = (config \ "epsilon").as[Double]
        lang = (config \ "language").asOpt[String].getOrElse("en")
        
        rightFlipFile = (config \ "right_flip_file").as[String]
        leftFlipFile = (config \ "left_flip_file").as[String]
        seedWordFile = (config \ "seed_word_file").as[String]
        
        vectorFile = (config \ "vector_file").as[String]
        similarityThreshold = (config \ "similarity_threshold").as[Double]
        
        super.initialize(config)
    }
    
    override def instantiate(data: List[Map[String, Any]]): ShortTextClassifier = {
        val jft = new JFastText
        jft.loadModel(utils.evaluateTuktuString(vectorFile, data.head))
        new ShortTextClassifier(minCount, jft, similarityThreshold)
    }
    
    override def train(data: List[Map[String, Any]], model: ShortTextClassifier): ShortTextClassifier = {
        // Add the documents
        val x = collection.mutable.ListBuffer.empty[List[String]]
        val y = collection.mutable.ListBuffer.empty[Double]
        
        data.map {datum =>
            x += datum(tokensField).asInstanceOf[String].split(" ").toList
            y += (datum(labelField) match {
                case l: Int => l.toDouble
                case l: String => l.toDouble
                case l: Double => l
                case _ => datum(labelField).toString.toDouble
            })
        }
        
        // Read the seed words from file
        val seedWords = {
            val f = scala.io.Source.fromFile(utils.evaluateTuktuString(seedWordFile, data.head))("utf8")
            val words = Json.parse(f.getLines.mkString).as[Map[String, List[String]]]
            f.close
            words
        }
        // Read the right and left flips from file
        val rightFlips = {
            val f = scala.io.Source.fromFile(utils.evaluateTuktuString(rightFlipFile, data.head))("utf8")
            val words = Json.parse(f.getLines.mkString).as[List[String]]
            f.close
            words
        }
        val leftFlips = {
            val f = scala.io.Source.fromFile(utils.evaluateTuktuString(leftFlipFile, data.head))("utf8")
            val words = Json.parse(f.getLines.mkString).as[List[String]]
            f.close
            words
        }
        
        // Train
        model.setWords(seedWords, rightFlips, leftFlips)
        model.trainClassifier(x.toList, y.toList, C, eps, utils.evaluateTuktuString(lang, data.head))
        
        model
    }
}

class ShortTextClassifierApplyProcessor(resultName: String) extends BaseMLApplyProcessor[ShortTextClassifier](resultName) {
    var dataField = ""
    var lang: String = _
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        lang = (config \ "language").asOpt[String].getOrElse("en")
        
        super.initialize(config)
    }
    
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: ShortTextClassifier): List[Map[String, Any]] = {
        data.map {datum =>
            datum + (resultName -> {
                model.predict(datum(dataField) match {
                    case s: Seq[String] => s.toList
                    case s: Array[String] => s.toList
                    case s: List[String] => s
                    case s: String => s.split(" ").toList
                    case _ => datum(dataField).toString.split(" ").toList
                }, utils.evaluateTuktuString(lang, data.head))
            }) 
        }
    }
}

class ShortTextClassifierDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[ShortTextClassifier](resultName) {
    var minCount: Int = _
    var vectorFile: String = _
    var similarityThreshold: Double = _
    
    override def initialize(config: JsObject) {
        minCount = (config \ "min_count").as[Int]
        vectorFile = (config \ "vector_file").as[String]
        similarityThreshold = (config \ "similarity_threshold").as[Double]
        
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new ShortTextClassifier(minCount, null, similarityThreshold)
        model.deserialize(filename)
        model
    }
}