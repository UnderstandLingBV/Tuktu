package tuktu.deeplearn.models.image

import java.io.File

import org.datavec.image.loader.NativeImageLoader
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport
import org.deeplearning4j.nn.modelimport.keras.trainedmodels.TrainedModels
import org.nd4j.linalg.dataset.api.preprocessor.VGG16ImagePreProcessor
import play.api.Play
import org.nd4j.linalg.api.ndarray.INDArray
import org.deeplearning4j.nn.modelimport.keras.trainedmodels.Utils.ImageNetLabels
import org.nd4j.linalg.factory.Nd4j
import java.net.URL
import javax.net.ssl.SSLHandshakeException

object VGG16 {
    // Get the files from
    // https://raw.githubusercontent.com/deeplearning4j/dl4j-examples/f9da30063c1636e1de515f2ac514e9a45c1b32cd/dl4j-examples/src/main/resources/trainedModels/VGG16.json
    // https://github.com/fchollet/deep-learning-models/releases/download/v0.1/vgg16_weights_th_dim_ordering_th_kernels.h5
    // See
    // https://github.com/deeplearning4j/deeplearning4j/blob/1f8af820c29cc5567a2c5eaa290f094c4d1492a7/deeplearning4j-modelimport/src/main/java/org/deeplearning4j/nn/modelimport/keras/trainedmodels/TrainedModels.java
    val vgg16 = {
        // Check if files exist
        val jf = Play.current.configuration.getString("tuktu.dl.vgg16.json").getOrElse("")
        val hf = Play.current.configuration.getString("tuktu.dl.vgg16.h5").getOrElse("")
        if (new File(jf).exists && new File(hf).exists)
            KerasModelImport.importKerasModelAndWeights(jf, hf, false)
        else null
    }
    
    def load() = vgg16 != null
    
    def classifyFile(filename: String, n: Int) = {
        if (vgg16 == null) List("unknown" -> 0.0f)
        else {
            // Convert file to INDArray
            val loader = new NativeImageLoader(224, 224, 3)
            val image = loader.asMatrix(new File(filename))
            
            // Mean subtraction pre-processing step for VGG
            val scaler = new VGG16ImagePreProcessor()
            scaler.transform(image)
            
            //Inference returns array of INDArray, index[0] has the predictions
            val output = vgg16.output(false, image)
            
            // Convert 1000 length numeric index of probabilities per label
            // to sorted return top 5 convert to string using helper function VGG16.decodePredictions
            // "predictions" is string of our results
            //TrainedModels.VGG16.decodePredictions(output(0))
            getLabels(output(0), n)
        }
    }
    
    def classifyFile(url: URL, n: Int) = {
        if (vgg16 == null) List("unknown" -> 0.0f)
        else {
            // Convert file to INDArray
            val loader = new NativeImageLoader(224, 224, 3)
            try {
                val image = loader.asMatrix(url.openStream)
                
                // Mean subtraction pre-processing step for VGG
                val scaler = new VGG16ImagePreProcessor()
                scaler.transform(image)
                
                //Inference returns array of INDArray, index[0] has the predictions
                val output = vgg16.output(false, image)
                
                // Convert 1000 length numeric index of probabilities per label
                // to sorted return top 5 convert to string using helper function VGG16.decodePredictions
                // "predictions" is string of our results
                //TrainedModels.VGG16.decodePredictions(output(0))
                getLabels(output(0), n)
            } catch {
                case e: SSLHandshakeException => List(("Unknown", 1.0))
            }
        }
    }
    
    def getLabels(predictions: INDArray, n: Int) = {
        val labels = ImageNetLabels.getLabels

        (0 to predictions.size(0) - 1).flatMap{batch =>
            val currentBatch = predictions.getRow(batch).dup
            
            for (i <- (0 to n - 1)) yield {
                val pos = Nd4j.argMax(currentBatch, 1).getInt(0, 0)
                val prob = currentBatch.getFloat(batch, pos)
                currentBatch.putScalar(0, pos, 0)
                val label = labels.get(pos)
                (label, prob)
            }
        } toList
    }
}