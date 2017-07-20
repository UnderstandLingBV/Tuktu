package tuktu.deeplearn.models.image

import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays
import scala.Ordering
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.ByteArrayOutputStream
import org.tensorflow.Graph
import org.tensorflow.Tensor

import play.api.Play

object TensorInceptionV3 {
    lazy val labels = {
        val file = new File(Play.current.configuration.getString("tuktu.dl.tensor.inception.labels").getOrElse(""))
        
        if(file.exists)
            Some(scala.io.Source.fromFile(file, "utf-8").getLines.toList)
        else
            None
    }
    
    lazy val session = {
        val file = new File(Play.current.configuration.getString("tuktu.dl.tensor.inception.pb").getOrElse(""))
        
        if(file.exists) {
            val graphDef = Files.readAllBytes(file.toPath)
            val g = new Graph
            g.importGraphDef(graphDef)
            Some(new org.tensorflow.Session(g))
        } else
            None
    }

    
    def classifyFile(filename: String, n: Int, useCategories: Boolean) = {
        if (!session.isDefined) List("unknown" -> 0.0f)
        else {
            val imageBytes = Files.readAllBytes(Paths.get(filename))
            getLabels(imageBytes, n, useCategories)
        }
    }
    
    def classifyFile(url: URL, n: Int, useCategories: Boolean) = {
        if (!session.isDefined) List("unknown" -> 0.0f)
        else {
            val conn = url.openConnection
            val baos = new ByteArrayOutputStream
            
            IOUtils.copy(conn.getInputStream, baos)
            val imageBytes = baos.toByteArray
            getLabels(imageBytes, n, useCategories)
        }
    }
        
    def getLabels(imageBytes: Array[Byte], n: Int, useCategories: Boolean) = {
        val image = Tensor.create(imageBytes)
        val labelProbabilities = executeInceptionGraph(session.get, image)
        
        val lbls = labelProbabilities.zipWithIndex.sortBy(_._1)(Ordering[Float].reverse).take(n).map { x => (labels.get(x._2), x._1) }.toList
        if (useCategories) lbls.map{lbl =>
            val lookup = lbl._1.replaceAll(" ", "_")
            util.categoryMap.get(lookup).getOrElse("chain_saw") -> lbl._2 // chain_saw resolves to object_other
        } else lbls
    }
    
    def executeInceptionGraph(s: org.tensorflow.Session, image: Tensor): Array[Float] = {
        val result = s.runner.feed("DecodeJpeg/contents", image).fetch("softmax").run.get(0)
        val rshape = result.shape
        if (result.numDimensions != 2 || rshape(0) != 1) {
            throw new RuntimeException(
                String.format(
                    "Expected model to produce a [1 N] shaped tensor where N is the number of labels, instead it produced one with shape %s",
                    Arrays.toString(rshape)))
        }
        val nlabels = rshape(1).toInt
        result.copyTo(Array.ofDim[Float](1, nlabels))(0)
    }
}