package tuktu.nlp.processors

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.nlp.models.FastTextWrapper
import tuktu.api.utils
import tuktu.nlp.models.FastTextCache

class FastTextProcessor(resultName: String) extends BaseProcessor(resultName) {
    //val models = collection.mutable.Map.empty[String, FastTextWrapper]
    var modelName: String = _
    var tokensField: String = _
    
    /*var lr: Double = _
    var lrUpdateRate: Int = _
    var dim: Int = _
    var ws: Int = _
    var epoch: Int = _
    var minCount: Int = _
    var minCountLabel: Int = _
    var neg: Int = _
    var wordNgrams: Int = _
    var lossName: String = _
    var ftModelName: String = _
    var bucket: Int = _
    var minn: Int = _
    var maxn: Int = _
    var thread: Int = _
    var t: Double = _
    var label: String = _
    var pretrainedVectors: String = _*/
    
    override def initialize(config: JsObject) {
        /*// Get all relevant parameters, with the defaults used by fastText
        lr = (config \ "learn_rate").asOpt[Double].getOrElse(0.05)
        lrUpdateRate = (config \ "learn_rate_update_rate").asOpt[Int].getOrElse(100)
        dim = (config \ "vector_size").asOpt[Int].getOrElse(100)
        ws = (config \ "window_size").asOpt[Int].getOrElse(5)
        epoch = (config \ "epochs").asOpt[Int].getOrElse(5)
        minCount = (config \ "min_count").asOpt[Int].getOrElse(5)
        minCountLabel = (config \ "min_count_label").asOpt[Int].getOrElse(0)
        neg = (config \ "negative").asOpt[Int].getOrElse(5)
        wordNgrams = (config \ "word_n_grams").asOpt[Int].getOrElse(1)
        lossName = (config \ "loss_name").asOpt[String].getOrElse("ns")
        ftModelName = (config \ "ft_model_name").asOpt[String].getOrElse("sg")
        bucket = (config \ "buckets").asOpt[Int].getOrElse(2000000)
        minn = (config \ "min_n_gram").asOpt[Int].getOrElse(3)
        maxn = (config \ "max_n_gram").asOpt[Int].getOrElse(6)
        thread = (config \ "threads").asOpt[Int].getOrElse(1)
        t = (config \ "sampling_threshold").asOpt[Double].getOrElse(1e-4)
        label = (config \ "label_prefix").asOpt[String].getOrElse("__label__")
        pretrainedVectors = (config \ "pretrained_vectors_file").asOpt[String].getOrElse("")*/
        
        modelName = (config \ "model_name").as[String]
        tokensField = (config \ "tokens_field").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(data.data.map {datum =>
            // See if we need to load a new model
            val newModelName = utils.evaluateTuktuString(modelName, datum)
            
            /*val pretrainedFile = utils.evaluateTuktuString(pretrainedVectors, datum)
            if (!models.contains(newModelName)) { 
                models += newModelName -> new FastTextWrapper(
                    lr, lrUpdateRate, dim, ws, epoch, minCount, minCountLabel, neg, wordNgrams,
                    lossName, ftModelName, bucket, minn, maxn, thread, t, label, pretrainedFile
                )
                models(modelName).deserialize(newModelName)
            }*/
            
            // Get our model from cache
            val model = FastTextCache.getModel(newModelName)
            
            // Predict
            val prediction = /*models(newModelName)*/model.predict(datum(tokensField) match {
                case a: String => a.split(" ")
                case a: Seq[String] => a
                case a: Any => a.toString.split(" ")
            })
            
            // Append
            datum + (resultName + "_label" -> prediction._1) + (resultName + "_score" -> prediction._2)
        })
    })
}