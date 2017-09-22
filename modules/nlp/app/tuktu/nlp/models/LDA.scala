package tuktu.nlp.models

import com.hankcs.lda.Corpus
import scala.collection.JavaConversions._
import com.hankcs.lda.LdaGibbsSampler
import com.hankcs.lda.LdaUtil

object LDA {
    def load(docs: List[String]) = {
        // Load a corpus from a list of documents (strings)
        val c = new Corpus
        docs.map {doc =>
            c.addDocument(doc.split(" ").filter(_.trim.size > 2).toList)
        }
        c
    }
    
    def getTopics(docs: List[String], maxGroups: Int) = {
        val corpus = load(docs)
        val sampler = new LdaGibbsSampler(corpus.getDocument, corpus.getVocabularySize)
        // Train
        sampler.gibbs(10)
        // Get topics
        val phi = sampler.getPhi
        LdaUtil.translate(phi, corpus.getVocabulary, maxGroups).toList
    }
}