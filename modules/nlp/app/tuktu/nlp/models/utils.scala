package tuktu.nlp.models

import java.text.BreakIterator
import java.util.Locale

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
        require(x.size == y.size)
        dotProduct(x, y) / (magnitude(x) * magnitude(y))
    }
    
    @scala.annotation.strictfp
    def cosineSimilarity(x: Array[java.lang.Float], y: Array[java.lang.Float]): java.lang.Float = {
        dotProduct(x, y) / (magnitude(x).toFloat * magnitude(y).toFloat)
    }


    /*
   * Return the dot product of the 2 arrays
   * e.g. (a[0]*b[0])+(a[1]*a[2])
   */
    def dotProduct(x: Array[Double], y: Array[Double]): Double = {
        (for ((a, b) <- x zip y) yield a * b) sum
    }

    @scala.annotation.strictfp
    def dotProduct(x: Array[java.lang.Float], y: Array[java.lang.Float]): java.lang.Float = {
        (for ((a, b) <- x zip y) yield a * b) sum
    } 
    
    /*
   * Return the magnitude of an array
   * We multiply each element, sum it, then square root the result.
   */
    def magnitude(x: Array[Double]): Double = {
        math.sqrt(x map (i => i * i) sum)
    }
    
    @scala.annotation.strictfp
    def magnitude(x: Array[java.lang.Float]): Double = {
        math.sqrt(x map (i => i * i) sum)
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