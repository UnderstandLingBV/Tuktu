package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import tuktu.api.BaseProcessor
import tuktu.api.utils
import tuktu.ml.processors.BaseMLApplyProcessor
import tuktu.ml.processors.BaseMLDeserializeProcessor
import tuktu.ml.processors.BaseMLTrainProcessor
import tuktu.nlp.models.ShortTextClassifier
import de.bwaldvogel.liblinear.FeatureNode

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
    var featuresToAdd: List[String] = _
    
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
        
        featuresToAdd = (config \ "features_to_add").asOpt[List[String]].getOrElse(Nil)
        
        super.initialize(config)
    }
    
    override def instantiate(data: List[Map[String, Any]]): ShortTextClassifier = new ShortTextClassifier(minCount)
    
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
        
        // Get all the features we need to add
        val vectorFeaturesToAdd = if (featuresToAdd.size > 0)
            data.map {datum =>
                (featuresToAdd.map {f =>
                    datum(f) match {
                        case _ => datum(f) match {
                            case n: Array[FeatureNode] => n
                            case n: Seq[FeatureNode] => n.toArray
                        }
                    }
                }).foldLeft(Array.empty[FeatureNode])(_ ++ _)
            } else Nil
        
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
        model.trainClassifier(x.toList, vectorFeaturesToAdd, y.toList, C, eps, utils.evaluateTuktuString(lang, data.head))
        
        model
    }
}

class ShortTextClassifierApplyProcessor(resultName: String) extends BaseMLApplyProcessor[ShortTextClassifier](resultName) {
    var dataField = ""
    var lang: String = _
    var featuresToAdd: List[String] = _
    var defaultClass: Option[String] = _
    
    override def initialize(config: JsObject) {
        dataField = (config \ "data_field").as[String]
        lang = (config \ "language").asOpt[String].getOrElse("en")
        featuresToAdd = (config \ "features_to_add").asOpt[List[String]].getOrElse(Nil)
        defaultClass = (config \ "default_class").asOpt[String]
        
        super.initialize(config)
    }
    
    override def applyModel(resultName: String, data: List[Map[String, Any]], model: ShortTextClassifier): List[Map[String, Any]] = {
        data.map {datum =>
            datum + (resultName -> {
                // Get all the features we need to add
                val vectorFeaturesToAdd = if (featuresToAdd.size > 0)
                    (featuresToAdd.map {f =>
                        datum(f) match {
                            case n: Array[FeatureNode] => n
                            case n: Seq[FeatureNode] => n.toArray
                        }
                    }).foldLeft(Array.empty[FeatureNode])(_ ++ _)
                    else Array.empty[FeatureNode]
                
                // Get default class if any
                val newDefaultClass = defaultClass match {
                    case Some(dc) => Some(utils.evaluateTuktuString(dc, datum).toInt)
                    case None => None
                }
                
                // Run the prediction
                model.predict(datum(dataField) match {
                    case s: Seq[String] => s.toList
                    case s: Array[String] => s.toList
                    case s: String => s.split(" ").toList
                    case _ => datum(dataField).toString.split(" ").toList
                }, vectorFeaturesToAdd, utils.evaluateTuktuString(lang, data.head), newDefaultClass)
            }) 
        }
    }
}

class ShortTextClassifierDeserializeProcessor(resultName: String) extends BaseMLDeserializeProcessor[ShortTextClassifier](resultName) {
    var minCount: Int = _
    
    override def initialize(config: JsObject) {
        minCount = (config \ "min_count").as[Int]
        
        super.initialize(config)
    }
    
    override def deserializeModel(filename: String) = {
        val model = new ShortTextClassifier(minCount)
        model.deserialize(filename)
        model
    }
}