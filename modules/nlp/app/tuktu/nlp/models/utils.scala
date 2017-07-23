package tuktu.nlp.models

import java.text.BreakIterator
import java.util.Locale
import org.jblas.DoubleMatrix
import org.jblas.FloatMatrix

/* 
 * Object in scala for calculating cosine similarity
 * Reuben Sutton - 2012
 * More information: http://en.wikipedia.org/wiki/Cosine_similarity
 */
@scala.annotation.strictfp
object CosineSimilarity {
    /*
   * This method takes 2 equal length arrays of integers 
   * It returns a double representing similarity of the 2 arrays
   * 0.9925 would be 99.25% similar
   * (x dot y)/||X|| ||Y||
   */
    def cosineSimilarity(x: Array[Double], y: Array[Double]): Double = {
        // Use BLAS
        cosineSimilarity(new DoubleMatrix(x), new DoubleMatrix(y))
    }
    
    def cosineSimilarity(vec1: DoubleMatrix, vec2: DoubleMatrix): Double = {
          vec1.dot(vec2) / (vec1.norm2() * vec2.norm2())
    }
    
    @scala.annotation.strictfp
    def cosineSimilarity(x: java.util.List[java.lang.Float], y: java.util.List[java.lang.Float]): java.lang.Float = {
        cosineSimilarity(new FloatMatrix(x), new FloatMatrix(y))
    }
    
    def cosineSimilarity(vec1: FloatMatrix, vec2: FloatMatrix): Float = {
          vec1.dot(vec2) / (vec1.norm2() * vec2.norm2())
    }
}

object NLP {
    def getNgramsChar(input: Seq[Char], n: Int) = {
        // Get N-grams as seq
        (for (i <- n to input.size - 1) yield {
            input.drop(i - n).take(n)
        }) toList
    }
    
    def getNgrams(input: Seq[String], n: Int) = {
        // Get N-grams as seq
        (for (i <- n to input.size) yield {
            input.drop(i - n).take(n)
        }) toList
    }
    
    def getSentences(line: List[String], language: String): List[String] = getSentences(line.mkString(" "), language)
    
    def getSentences(line: String, language: String): List[String] = {
        val bi = BreakIterator.getSentenceInstance(language match {
            case "ar" => new Locale("ar")
            case "jp" => Locale.JAPANESE
            case "zh" => Locale.CHINESE
            case "de" => Locale.GERMAN
            case _ => Locale.ENGLISH
        })
        bi.setText(line)
        
        def process(start: Int, end: Int, next: Int): List[String] = {
            if (next == BreakIterator.DONE) Nil
            else {
                val n = bi.next
                line.substring(start, end)::process(end, n, n)
            }
        }
        val n = bi.next
        process(0, n, n)
    }
}