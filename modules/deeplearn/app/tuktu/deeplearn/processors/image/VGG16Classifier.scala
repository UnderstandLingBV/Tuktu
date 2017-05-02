package tuktu.deeplearn.processors.image

import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.utils
import java.net.URL
import tuktu.deeplearn.models.image.VGG16

/**
 * Classifies an image to find what is represented on it using VGG16 model
 */
class VGG16Classifier(resultName: String) extends BaseProcessor(resultName) {
    var localRemote = "remote"
    var imageName: String = _
    var n: Int = _
    var flatten: Boolean = false
    
    override def initialize(config: JsObject) {
        (config \ "local_remote").asOpt[String] match {
            case Some(lr) if lr == "local" => localRemote = "local"
            case _ => 
        }
        imageName = (config \ "image_name").as[String]
        n = (config \ "top_n").asOpt[Int].getOrElse(3)
        flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        data.map{datum =>
            // Get image
            val imageUri = utils.evaluateTuktuString(imageName, datum)
            
            datum + (resultName -> {
                if (localRemote == "remote") {
                    // Get URL and classify
                    val url = new URL(imageUri)
                    val labels = VGG16.classifyFile(url, if (flatten) 1 else n)
                    if (flatten) labels.head else labels
                } else {
                    // Local file
                    val labels = VGG16.classifyFile(imageUri, if (flatten) 1 else n)
                    if (flatten) labels.head else labels
                }
            })
        }
    })
}