package tuktu.deeplearn.models.image

import java.net.URL
import java.nio.file.{ Files, Paths }
import java.util.Arrays
import scala.Ordering
import org.apache.commons.io.IOUtils
import org.tensorflow.{ Graph, Tensor }

import play.api.Play
import scala.util.Try

object TensorInceptionV3 {
    lazy val labels: Option[List[String]] =
        Play.current.configuration.getString("tuktu.dl.tensor.inception.labels").flatMap { file =>
            val path = Paths.get(file)

            if (Files.isRegularFile(path)) {
                val reader = scala.io.Source.fromFile(path.toFile, "utf-8")
                val result = Try { reader.getLines.toList }.toOption
                reader.close
                result
            } else
                None
        }

    lazy val session =
        Play.current.configuration.getString("tuktu.dl.tensor.inception.pb").flatMap { file =>
            val path = Paths.get(file)

            if (Files.isRegularFile(path)) {
                val graphDef = Files.readAllBytes(path)
                val g = new Graph
                g.importGraphDef(graphDef)
                Option(new org.tensorflow.Session(g))
            } else
                None
        }

    def classifyFile(filename: String, n: Int, useCategories: Boolean): List[(String, Float)] = session.map { session =>
        val imageBytes = Files.readAllBytes(Paths.get(filename))
        getLabels(session, imageBytes, n, useCategories)
    }.getOrElse(List("unknown" -> 0.0f))

    def classifyFile(url: URL, n: Int, useCategories: Boolean): List[(String, Float)] = session.flatMap { session =>
        val is = url.openStream
        val result = Try {
            val imageBytes = IOUtils.toByteArray(is)
            getLabels(session, imageBytes, n, useCategories)
        }
        is.close
        result.toOption
    }.getOrElse(List("unknown" -> 0.0f))

    def getLabels(session: org.tensorflow.Session, imageBytes: Array[Byte], n: Int, useCategories: Boolean): List[(String, Float)] = {
        val image = Tensor.create(imageBytes)
        val labelProbabilities = executeInceptionGraph(session, image)

        val lbls = labelProbabilities.zipWithIndex.sortBy(_._1)(Ordering[Float].reverse).take(n).map { x => (labels.get(x._2), x._1) }.toList
        if (useCategories) lbls.map { lbl =>
            val lookup = lbl._1.replaceAll(" ", "_")
            util.categoryMap.getOrElse(lookup, "chain saw") -> lbl._2 // chain_saw resolves to object_other
        }
        else lbls
    }

    def executeInceptionGraph(session: org.tensorflow.Session, image: Tensor): Array[Float] = {
        val result = session.runner.feed("DecodeJpeg/contents", image).fetch("softmax").run.get(0)
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