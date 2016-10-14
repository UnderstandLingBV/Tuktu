package tuktu.nlp.models

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import play.api.libs.json._
import java.io._

object Rhetorics {
    case class Conjunctions(conjunctions: List[String], conjunctionWords: List[String], determiners: List[String]) {
        def this(conj: Conjunctions) = this(conj.conjunctions, conj.conjunctionWords, conj.determiners)
        def this(language: String) = this(getConjunctions(language))
    }
    
    var conjunctions = scala.collection.mutable.Map[String, Conjunctions]()
    
    /**
     * Loads the conjunctions either from memory if available or from disk into memory
     * @param language: String The language
     */
    def getConjunctions(language: String): Conjunctions = {
        if (conjunctions.contains(language))
            conjunctions(language)
        else {
            // Load conjunctions from disk
            val br = new BufferedReader(new InputStreamReader(getClass.getResourceAsStream("/" + language + "-conj.json")))
            val sb = new StringBuilder()
            var read: String = br.readLine()
            while(read != null) {
                sb ++= read
                read = br.readLine()
            }
            br.close()
            val conjJson = Json.parse(sb.toString)
            val conj  = (conjJson \ "conjunctions")    .as[JsArray].value.map(_.as[JsString].value).toList
            val words = (conjJson \ "conjunctionWords").as[JsArray].value.map(_.as[JsString].value).toList
            val det   = (conjJson \ "determiners")     .as[JsArray].value.map(_.as[JsString].value).toList
            
            conjunctions += ((language, new Conjunctions(conj, words, det)))
            conjunctions(language)
        }
    }
    
    def toJavaMap(m: Map[String, Int]) = {
        mapAsJavaMap(m).asInstanceOf[java.util.Map[java.lang.String, java.lang.Integer]]
    }

    /**
     * Finds rhetoric patterns in a message
     * @param tokens List[String] The tokens of the message
     * @param tags List[String] The POS-tags of the message
     * @param language String The language of the message
     * @return Map[String, Int] The number of rhetoric patterns ("epizeuxis", "polysyndeton", "anaphora", "epistrophe", "epanalepsis", "anadiplosis", "ploche", "antimetabole") found
     */
    def find(tokens: Array[String], tags: Array[String], language: String): Map[String, Int] = {
        val found = find(tokens.toList, tags.toList, language)
        Map(
                "epizeuxis" -> found._1,
                "polysyndeton" -> found._2,
                "anaphora" -> found._3,
                "epistrophe" -> found._4,
                "epanalepsis" -> found._5,
                "anadiplosis" -> found._6,
                "ploche" -> found._7,
                "antimetabole" -> found._8,
                "alliteration" -> found._9,
                "polyptoton" -> found._10
        )
    }    
    
    /**
     * Finds rhetoric patterns in a message
     * @param originalTokens List[String] The tokens of the message
     * @param tags List[String] The POS-tags of the message
     * @param language String The language of the message
     * @return (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int) The number of ("epizeuxis", "polysyndeton", "anaphora", "epistrophe", "epanalepsis",
     *      "anadiplosis", "ploche", "antimetabole", "alliteration", "polyptoton") in the given message
     */
    def find(originalTokens: List[String], tags: List[String], language: String): (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int) = {
        val tokens = originalTokens.map(elem => elem.toLowerCase)
        
        val conj = getConjunctions(language)
        
        val conjunctions = conj.conjunctions
        val conjunctionWords = conj.conjunctionWords
        val determiners = conj.determiners

        // Stops are ! ? . and continuators are , : ;
        
        // For some patterns, we want to ignore punctuation
        val unPunctuated = tokens.filter(token => !List(";", ",", ".", "!", "?", ":").contains(token))
        // We also want to obtain 'sentences'
        val sentences = tokens.mkString(" ").split(" ; | \\. | \\! | \\? | : ").toList
            .map(sentence => sentence.replaceAll(";|\\.|\\!|\\?|:", "").trim.split(" ").toList).filter(elem => {
                if (elem.size == 0) false else (if (elem.size == 1 && elem.head == "") false else true)
            })
        // Phrases also can split on comma and conjunction words
        val phrases = tokens.mkString(" ").split(" ; | , | \\. | \\! | \\? | : " + conjunctionWords.mkString(" | ", " | ", "")).toList
            .map(sentence => sentence.replaceAll(";|,|\\.|\\!|\\?|:", "").trim.split(" ").toList).filter(elem => {
                if (elem.size == 0) false else (if (elem.size == 1 && elem.head == "") false else true)
            })
            
            
        /**
         * Finds expizeuxis
         * def
         *      Repetition of words or phrases with no other words in between
         * crit
         *      Two or more occurrences of the word next to each other
         *      Special characters are omitted
         * ex
         *      Give me a break! Give me a break! Break me off a piece of that Kit Kat bar!
         *      Horror, horror, horror
         * @param words List[String] The words of a message
         */
        def epizeuxisFinder(words: List[String]) = {
            /**
             * Recursively goes through remaining words to find an expizeuxis
             * @param remainWords List[String] The remaining word list
             */
            def wordTraverser(remainingWords: List[String]): Int = remainingWords match {
                case List() => 0
                case word::tail => {
                    // Keep track of a trail
                    var trail = collection.mutable.ListBuffer[String]()
                    // Go through all other words
                    val matchIndex = (for ((tailWord, index) <- tail.zipWithIndex) yield {
                        // See if this word matches our initial word
                        if (word == tailWord) {
                            // Check if they are equal
                            if (trail.toList == tail.drop(index + 1).take(trail.size))
                                index // Match!
                            else
                                -1
                        }
                        else {
                            // Build up the trail
                            trail += tailWord
                            -1
                        }
                    }).toList.filter(elem => elem != -1)
                    
                    if (matchIndex.size > 0) {
                        // We found a match, add 1 and skip the trail
                        1 + wordTraverser(tail.drop(matchIndex.head))
                    }
                    else {
                        // No match found, skip 1 word
                        wordTraverser(tail)
                    }
                }
            }
            
            wordTraverser(words)
        }
        
        /**
         * Finds polysyndeton
         * def
         *      Employing many conjunctions between clauses, slowing down tempo of expression
         * crit
         *      Sliding window is two sentences
         *          Polysyndeton occurs only in one sentence
         *          The same conjunctions begin two consecutive sentences
         *       If several conjunctions occur in the middle of two (or more) sentences, they will be treated as separate polysyndetons.
         * ex
         *      Let the whitefolks have their money and power and segregation and sarcasm and big houses and schools and lawns like carpets, and books, and mostly--mostly--let them have their whiteness."
         * @param words List[String] The words of a message
         */
        def polysyndetonFinder() = {
            // First part, find two or more conjunctions in one sentence
            val part1 = (for (sentence <- sentences) yield {
                // Find duplicate conjunctions
                val duplicates = sentence.filter(word => {
                            conjunctionWords.contains(word) || {try {
                                conjunctions.contains(tags(tokens.indexOf(word)))
                            } catch {
                                case e: Exception => false
                            }}
                        }).groupBy(elem => elem)
                        .map(elem => elem._1 -> elem._2.size).filter(elem => elem._2 > 2)
                duplicates.size
            }).toList.foldLeft(0)(_ + _)
            
            // Second part, find sentences that start with the same conjunction
            val part2 = (for (i <- 0 to sentences.size - 1; j <- i + 1 to sentences.size - 1) yield {
                // Get the first words of the sentence
                val s1 = sentences(i)
                val s2 = sentences(j)
                if (s1.size > 0 && s2.size > 0) {
                    // Get the tages
                    try {
                        val t1 = tags(tokens.indexOf(s1.head))
                        val t2 = tags(tokens.indexOf(s2.head))
                        if ((conjunctions.contains(t1) || conjunctionWords.contains(s1.head)) &&
                            (conjunctions.contains(t2) || conjunctionWords.contains(s2.head))) {
                                if (s1(0) == s2(0)) 1
                                else 0
                        } else 0
                    }
                    catch {
                        case e: Exception => {
                            // Malformed tokens may not work out well
                            0
                        }
                    }
                }
                else 0
            }).toList.foldLeft(0)(_+_)
            
            // Return sum
            part1 + part2
        }
        
        /**
         * Finds anaphora
         * def
         *      Repetition of same word or phrases at the beginning of clauses
         * crit
         *      Find all repetitions within sliding windows
         *      Check if they are located at beginning of clause or phrase
         *      Assumptions
         *          Don’t take conjunctions, prepositions, determiners that start a clause into consideration
         *          The length of a syntactic unit (phrase, clause, or sentence) in which we look for a anaphora, counted
         *              in the number of words, has to be equal or greater than some specified number min_length (3 in this case)
         * ex
         *      Choose wisely. Choose Sony.
         */
        def anaphoraFinder(): Int = {
            // Find phrases of sufficient length
            val phrasesToUse = phrases.filter(phrase => phrase.size > 2)
            (for (i <- 1 to phrasesToUse.size - 1) yield {
                // Get the two consecutive phrases
                val s1 = phrasesToUse(i - 1)
                val s2 = phrasesToUse(i)
                if (s1.size > 0 && s2.size > 0) {
                    // Get the tags to see if these phrases are conjunctions or determiners
                    try {
                        val t1 = tags(tokens.indexOf(s1.head))
                        val t2 = tags(tokens.indexOf(s2.head))
                        if (!conjunctions.contains(t1) && !determiners.contains(t1) &&
                                !conjunctions.contains(t2) && !determiners.contains(t2) &&
                                !conjunctionWords.contains(s1.head) && !conjunctionWords.contains(s2.head)) {
                            if (s1(0) == s2(0)) 1
                            else 0
                        } else 0
                    }
                    catch {
                        case e: Exception => 0
                    }
                }
                else 0
            }).toList.fold(0)(_ + _)
        }
        
        /**
         * Finds epistrophe
         * def
         *      Ending the clauses with the same words or phrases
         * crit
         *      Change instructions of anaphora from beginning to end of clause or phrase
         * ex
         *      I'm a Pepper, he's a Pepper, she's a Pepper, we're a Pepper. Wouldn't you like to be a Pepper, too? Dr. Pepper."
         */
        def epistropheFinder() = {
            // Find phrases of sufficient length
            val phrasesToUse = phrases.filter(phrase => phrase.size > 2).map(phrase => phrase.reverse)
            (for (i <- 1 to phrasesToUse.size - 1) yield {
                // Get the two consecutive phrases, reversed order
                val s1 = phrasesToUse(i - 1)
                val s2 = phrasesToUse(i)
                if (s1.size > 0 && s2.size > 0) {
                    // Get the tags to see if these phrases are conjunctions or determiners
                    try {
                        val t1 = tags(tokens.indexOf(s1.head))
                        val t2 = tags(tokens.indexOf(s2.head))
                        if (!conjunctions.contains(t1) && !determiners.contains(t1) &&
                                !conjunctions.contains(t2) && !determiners.contains(t2) &&
                                !conjunctionWords.contains(s1.head) && !conjunctionWords.contains(s2.head)) {
                            if (s1(0) == s2(0)) 1
                            else 0
                        } else 0
                    }
                    catch {
                        case e: Exception => 0
                    }
                }
                else 0
            }).toList.fold(0)(_ + _)
        }
        
        /**
         * Finds epanalepsis
         * def
         *      Repetition at the end of a line, phrase, or clause of the word or words that occurred at the beginning of the same line, phrase, or clause
         * crit
         *      First, find occurrence of word or group of words at beginning
         *      Second, occurrence at end of clause/phrase/line/syntactic unit
         * ex
         *      Always Low Prices. Always.
         */
        def epanalepsisFinder(): Int = {
            // In a sentence
            (for (sentence <- sentences) yield {
                // Remove punctuation
                val sentNoPunct = sentence.filter(token => !List(";", ",", ".", "!", "?", ":").contains(token))
                if (sentNoPunct.size > 1) {
                    // Reverse the sentence
                    val revSent = sentNoPunct.reverse
                    // Get the first word of the original, find the last occurrence of it in the reversed one
                    val firstWord = sentNoPunct.head
                    // Sanity check: should occur at least twice
                    val sameWords = revSent.zipWithIndex.filter(word => word._1 == firstWord)
                    if (sameWords.size > 1) {
                        // Find the first occurrence of the first word of the original in the reversed (hence being the last occurrence of the same word)
                        val startIndex = sameWords.head._2
                        // Take the words up to and including the startIndex and reverse it for checking
                        val trailingPhrase = revSent.take(startIndex + 1).reverse
                        // See if they are equal
                        if ((for ((word, index) <- trailingPhrase.zipWithIndex) yield {
                            word == unPunctuated(index)
                        }).toList.foldLeft(true)(_ && _)) 1 else 0
                    } else 0
                } else 0
            }).toList.foldLeft(0)(_+_) + 
            // In the entire message
                (if (unPunctuated.size > 1) {
                    // Reverse the sentence
                    val revSent = unPunctuated.reverse
                    // Get the first word of the original, find the last occurrence of it in the reversed one
                    val firstWord = unPunctuated.head
                    // Sanity check: should occur at least twice
                    val sameWords = revSent.zipWithIndex.filter(word => word._1 == firstWord)
                    if (sameWords.size > 1) {
                        // Find the first occurrence of the first word of the original in the reversed (hence being the last occurrence of the same word)
                        val startIndex = sameWords.head._2
                        // Take the words up to and including the startIndex and reverse it for checking
                        val trailingPhrase = revSent.take(startIndex + 1).reverse
                        // See if they are equal
                        if ((for ((word, index) <- trailingPhrase.zipWithIndex) yield {
                            word == unPunctuated(index)
                        }).toList.foldLeft(true)(_ && _)) 1 else 0
                    } else 0
                } else 0)
        }
        
        /**
         * Finds anadiplosis
         * def
         *      Repetition of last word or phrase of clause to begin the next
         * crit
         *      First, find occurrence of word or group of words at end of clause
         *      Second, occurrence at beginning of succeeding clause/phrase/line/syntactic unit
         *          Omit determiners, conjunctions at the beginning of succeeding line
         * ex
         *      Only the brave deserve the fair and the fair deserve Jaeger.
         */
        def anadiplosisFinder(): Int = {
            (for (i <- 1 to phrases.size - 1) yield {
                // Get the first sentence in reverse
                val s1 = phrases(i - 1).reverse
                val s2 = phrases(i)
                // Find the occurrence of the last word of the first sentence in the second one
                if (s1.size > 0) {
                    if (s2.contains(s1.head)) {
                        // Get the phrase that exists in s2
                        val subphrase2 = s2.take(s2.indexOf(s1.head) + 1).reverse
                        // Get the subphrase of s1
                        val subphrase1 = s1.take(subphrase2.size)
                        if (subphrase1 == subphrase2) 1 else 0
                    } else 0
                } else 0
            }).toList.fold(0)(_ + _)
        }
        
        /**
         * Finds ploche
         * def
         *      Repetition of a single word for rhetorical emphasis
         * crit
         *      Sliding window of two sentences
         *      Not considered if in three successive sentences
         *          If in sentence 1, then sentence 3, not counted
         *      If a word is repeated three or more times, or if it occurs twice in the same sentence, or in two neighbouring sentences, we consider it a ploche
         * ex
         *      I am stuck on Band-Aid, and Band-Aid's stuck on me
         */
        def plocheFinder(): Int = {
            // Single word, repeated twice in two different sentences
            val part1 = (for (i <- 1 to phrases.size - 1) yield {
                // Get two consecutive phrases
                val p1 = phrases(i - 1)
                val p2 = phrases(i)
                // Find all words that occur in both and that are not conjunctions or determiners
                val cnt = try {
                    p1.filter(word => p2.contains(word) &&
                            !conjunctionWords.contains(word) &&
                            !conjunctions.contains(tags(tokens.indexOf(word))) &&
                            !determiners.contains(tags(tokens.indexOf(word)))).distinct.size
                } catch {
                    case e: Exception => 0
                }
                i - 1 -> cnt
            }).toMap
            // Occurs twice in the same phrase
            val part2 = (for ((phrase, i) <- phrases.zipWithIndex) yield {
                // Group all words together that are not conjunctions or determiners
                val cnt = try {
                    phrase.filter(word => !conjunctionWords.contains(word) &&
                        !conjunctions.contains(tags(tokens.indexOf(word))) &&
                        !determiners.contains(tags(tokens.indexOf(word)))).groupBy(word => word).filter(wordGroup => wordGroup._2.size > 1).size
                } catch {
                    case e: Exception => 0
                }
                i -> cnt
            }).toMap
            // Repeated 3 or more times
            val part3 = {
                val tokensToUse = phrases.zipWithIndex.filter(phraseIndex => !part1.filter(phraseCount => phraseCount._2 > 0).contains(phraseIndex._2)
                        && !part2.filter(phraseCount => phraseCount._2 > 0).contains(phraseIndex._2))
                        .map(phraseIndex => phraseIndex._1).flatten
                try {
                    tokensToUse.filter(word => !conjunctionWords.contains(word) &&
                        !conjunctions.contains(tags(tokens.indexOf(word))) &&
                        !determiners.contains(tags(tokens.indexOf(word)))).groupBy(word => word).filter(wordGroup => wordGroup._2.size > 2).size
                } catch {
                    case e: Exception => 0
                }    
            }
            
            // Sum it all up
            part1.foldLeft(0)(_+_._2) + part2.foldLeft(0)(_+_._2) + part3
        }
        
        /**
         * Finds antimetabole
         * def
         *      Repetition of words, in successive clauses, in reverse grammatical order
         * crit
         *      Detect repeating groups of words
         *      Search for word palindromes that occur in close vicinity, but not necessarily neighboring
         *      Often, direct object in first clause becomes subject in second
         * ex
         *      Stop static before static stop you.
         */
        def antimetaboleFinder(): Int = {
            // Remove conjunctions and determiners
            val phrasesToUse = phrases.map(phrase => phrase.filter(word => {
                !conjunctionWords.contains(word) && {try {
                    val tag = tags(tokens.indexOf(word))
                    !determiners.contains(tag) && !conjunctions.contains(tag)
                } catch {
                    case e: Exception => true
                }}
            }))
            
            (for (i <- 1 to phrasesToUse.size - 1) yield {
                // Get the first sentence in revers
                val s1 = phrasesToUse(i - 1).reverse
                val sx2 = phrasesToUse(i)
                val s2 = if (s1.size > 0 && sx2.contains(s1.head)) sx2.drop(sx2.indexOf(s1.head)) else List()
                
                // Find the occurrence of the last word of the first sentence in the second one
                // and make sure there are at least 2 repetitive words
                if (s1.size > 1 && s2.size > 1) {
                    if ((s1(0).take(s1(0).size - 1) == s2(0) || s1(0) == s2(0).take(s2(0).size - 1) ||
                            s1(0) == s2(0) || s1(0).take(s1(0).size - 1) == s2(0).take(s2(0).size - 1)) &&
                            (s1(1).take(s1(1).size - 1) == s2(1) || s1(1) == s2(1).take(s2(1).size - 1) ||
                                s1(1) == s2(1) || s1(1).take(s1(1).size - 1) == s2(1).take(s2(1).size - 1))) {
                        // Two words match
                        1
                    } else 0
                } else 0
            }).toList.fold(0)(_ + _)
        }
        
        /**
         * Finds alliteration
         * def
         *      Repetition of initial consonant in closely connected words
         * crit
         *      Repetition of initial or medial consonants in two or more adjacent words
         * ex
         *      Wonderful weather!
         */
        def alliterationFinder(): Int = {
            // Find all consecutive words that start with the same character
            (for (i <- 1 to tokens.size - 1) yield {
                // Compare first chars of 2 consecutive words
                if (tokens(i - 1).length > 0 && tokens(i).length > 0)
                    if (tokens(i - 1)(0) == tokens(i)(0)) 1 else 0
                    else 0
            }).toList.foldLeft(0)(_+_)
        }       

        /**
         * Finds polyptoton
         * def
         *      Repetition of words derived from same root, but with different endings
         * crit
         *      Stems should be the same
         * ex
         *      The only thing we have to fear is fear itself.
         */
        def polyptotonFinder(): Int = {
            // Find all words that have the same stem (same characters of at least length 3 and different endings -> at least 1 char different)
            (for (i <- 0 to tokens.size - 1) yield {
                (for (j <- i + 1 to tokens.size - 1) yield {
                    val word1 = tokens(i)
                    val word2 = tokens(j)
                    // Check if the words have at least 4 chars
                    if (word1.length >= 4 && word2.length >= 4) {
                        if (word1.take(3) == word2.take(3) && word1.drop(3) != word2.drop(3)) 1 else 0
                    } else 0
                }).toList
            }).toList.flatten.foldLeft(0)(_ + _)
        }
        
        
        /*  1.  Epizeuxis */
        val epizeuxis = epizeuxisFinder(unPunctuated)
        
        /*  2.  Polysyndeton */
        val polysyndeton = polysyndetonFinder()
        
        /*  3.  Anaphora */
        val anaphora = anaphoraFinder()
        
        /* 4.   Epistrophe */
        val epistrophe = epistropheFinder()
        
        /* 5.   Epanalepsis */
        val epanalepsis = epanalepsisFinder()
        
        /* 6.   Anadiplosis */
        val anadiplosis = anadiplosisFinder()
        
        /* 7.   Ploche */
        val ploche = plocheFinder()
        
        /* 8.   Antimetabole */
        val antimetabole = antimetaboleFinder()
        
        /* 9.   Alliteration */
        val alliteration = alliterationFinder()
        
        /* 10.  Polyptoton */
        val polyptoton = polyptotonFinder()

        (epizeuxis, polysyndeton, anaphora, epistrophe, epanalepsis, anadiplosis, ploche, antimetabole, alliteration, polyptoton)
    }
    
    /**
     * Returns the persuasion score for a list of tokens and tags with given weights for the 8 rhetoric patterns
     * @param originalTokens List[String] The tokens of the message
     * @param tags List[String] The POS-tags of the message
     * @param weights List[Double] A list of weights to weight the (currently) 8 supported rhetoric patterns
     * @param language String The language of the message
     * @return Double The final persuasion score 
     */
    def persuasionScore(originalTokens: List[String], tags: List[String], weights: List[Double], language: String): Double = {
        if(weights.length != 8) {
            throw new Exception("InvalidArgumentException: weights has to have 8 elements.")
        }
        
        val patterns = find(originalTokens, tags, language)
        
        weights(0) * patterns._1 + weights(1) * patterns._2 + weights(2) * patterns._3 + weights(3) * patterns._4 +
        weights(4) * patterns._5 + weights(5) * patterns._6 + weights(6) * patterns._7 + weights(7) * patterns._8
    }
    
    def messagePersuasionScore(language: String, tokens: List[String], tags: List[String], typeWeights: Map[String, java.lang.Double], emotions: Map[String, java.lang.Double]): java.lang.Double = {
        messagePersuasionScore(language, tokens, tags, typeWeights.map(wgt => wgt._1 -> wgt._2.toDouble), emotions.map(emo => emo._1 -> emo._2.toDouble)).asInstanceOf[java.lang.Double]
    }
    
    /**
     * Computes the persuasion score for a message, aggregated over the 6 different types of persuasions we have:
     *   - Facebook comments
     *   - Facebook likes
     *   - Facebook shares
     *   - Twitter replies
     *   - Twitter favorites
     *   - Twitter retweets
     * @param language String The language of the message
     * @param tokens List[String] The tokens of the message
     * @param tags List[String] The POS-tags of the message
     * @param typeWeights Map[String, Double] A mapping from the type of score to the actual weight, sum should probably be 1
     * @param emotions Map[String, Double] The emotion scores for the message
     * @return Double The persuasion score
     */
    def messagePersuasionScore(language: String, tokens: List[String], tags: List[String], typeWeights: Map[String, Double], emotions: Map[String, Double]): Double = {
        // Compute the scores of all the different types
        typeWeights("comments") * (
                (
                   List(
                       Math.max(emotions("joySadness"), 0),
                       Math.min(emotions("joySadness"), 0),
                       Math.max(emotions("trustDisgust"), 0),
                       Math.min(emotions("trustDisgust"), 0),
                       Math.max(emotions("fearAnger"), 0),
                       Math.min(emotions("fearAnger"), 0),
                       Math.max(emotions("surpriseAnticipation"), 0),
                       Math.min(emotions("surpriseAnticipation"), 0)
                   ).zip(List(
                        0.01243725278997453,
                        -0.07698199310088816,
                        -0.03707076588320809,
                        0.13780319691651857,
                        -0.06650589424456294,
                        -0.11494084315100916,
                        0.0,
                        0.14828830220826394
                   )).foldLeft(0.0)((a,b) => a + (b._1 * b._2))
                ) +
                persuasionScore(tokens, tags, List(
                    -0.18310024752990925,
                    0.019399340616698457,
                    0.05656322767942813,
                    -0.005348645123531657,
                    -0.07781453194950967,
                    0.01759859854897186,
                    0.013402842482725054,
                    0.0
                ), language)
        ) +
        typeWeights("likes") * (
                (
                   List(
                       Math.max(emotions("joySadness"), 0),
                       Math.min(emotions("joySadness"), 0),
                       Math.max(emotions("trustDisgust"), 0),
                       Math.min(emotions("trustDisgust"), 0),
                       Math.max(emotions("fearAnger"), 0),
                       Math.min(emotions("fearAnger"), 0),
                       Math.max(emotions("surpriseAnticipation"), 0),
                       Math.min(emotions("surpriseAnticipation"), 0)
                   ).zip(List(
                        -0.004155879437276768,
                        -0.008453516154413762,
                        -0.00048134932635483503,
                        0.007309854196279558,
                        -0.0035457964960633847,
                        -0.01579472651480441,
                        0.0,
                        0.006805643194034698
                   )).foldLeft(0.0)((a,b) => a + (b._1 * b._2))
                ) +
                persuasionScore(tokens, tags, List(
                    -0.013931341038583098,
                    -0.0020157593960464914,
                    0.0033428955699532975,
                    -0.00874163807440474,
                    -0.012522774121273627,
                    -0.0027326774977177695,
                    0.002755706656549446,
                    0.0
                ), language)
        ) +
        typeWeights("shares") * (
                (
                   List(
                       Math.max(emotions("joySadness"), 0),
                       Math.min(emotions("joySadness"), 0),
                       Math.max(emotions("trustDisgust"), 0),
                       Math.min(emotions("trustDisgust"), 0),
                       Math.max(emotions("fearAnger"), 0),
                       Math.min(emotions("fearAnger"), 0),
                       Math.max(emotions("surpriseAnticipation"), 0),
                       Math.min(emotions("surpriseAnticipation"), 0)
                   ).zip(List(
                        -0.0038668272284717326,
                        -0.03365839484016331,
                        0.002692688083527883,
                        -0.0037499982577047057,
                        -0.010972176970288176,
                        -0.025582429309074426,
                        0.0,
                        -0.017200943402839373
                   )).foldLeft(0.0)((a,b) => a + (b._1 * b._2))
                ) +
                persuasionScore(tokens, tags, List(
                    -0.012070725443170198,
                    -0.00121481517554659,
                    0.023967307706185804,
                    -0.003330663240129936,
                    -0.028435621568767562,
                    -0.012255856107876092,
                    0.004065130622696185,
                    0.0
                ), language)
        ) +
        typeWeights("favorites") * (
                (
                   List(
                       Math.max(emotions("joySadness"), 0),
                       Math.min(emotions("joySadness"), 0),
                       Math.max(emotions("trustDisgust"), 0),
                       Math.min(emotions("trustDisgust"), 0),
                       Math.max(emotions("fearAnger"), 0),
                       Math.min(emotions("fearAnger"), 0),
                       Math.max(emotions("surpriseAnticipation"), 0),
                       Math.min(emotions("surpriseAnticipation"), 0)
                   ).zip(List(
                        0.024800032344657878,
                        -0.004777641963760246,
                        0.011236949666210572,
                        -0.04339042539556042,
                        -0.030005299865366146,
                        0.002133167387342567,
                        0.041277389517411056,
                        -0.0004372555968961137
                   )).foldLeft(0.0)((a,b) => a + (b._1 * b._2))
                ) +
                persuasionScore(tokens, tags, List(
                    -0.03446012845802453,
                    0.012949373505111439,
                    0.0853601071307724,
                    -0.021440689248736336,
                    0.01706202999811805,
                    0.027214092339941938,
                    0.0327978948112069,
                    -0.01979428323038217
                ), language)
        ) +
        typeWeights("replies") * (
                (
                   List(
                       Math.max(emotions("joySadness"), 0),
                       Math.min(emotions("joySadness"), 0),
                       Math.max(emotions("trustDisgust"), 0),
                       Math.min(emotions("trustDisgust"), 0),
                       Math.max(emotions("fearAnger"), 0),
                       Math.min(emotions("fearAnger"), 0),
                       Math.max(emotions("surpriseAnticipation"), 0),
                       Math.min(emotions("surpriseAnticipation"), 0)
                   ).zip(List(
                        0.004400741996761045,
                        -0.004451300969127002,
                        -0.010001832998610426,
                        -0.016131043681605353,
                        -0.021103166405406347,
                        0.04747099651542806,
                        0.03413266707674853,
                        -0.015599806250490568
                   )).foldLeft(0.0)((a,b) => a + (b._1 * b._2))
                ) +
                persuasionScore(tokens, tags, List(
                    -0.024638623812557447,
                    -0.02132952044777881,
                    0.016408800542881657,
                    0.008631323497999523,
                    -0.013922151701612094,
                    0.03556666749445554,
                    0.005117266695653109,
                    -0.011699892105854906
                ), language)
        ) +
        typeWeights("retweets") * (
                (
                   List(
                       Math.max(emotions("joySadness"), 0),
                       Math.min(emotions("joySadness"), 0),
                       Math.max(emotions("trustDisgust"), 0),
                       Math.min(emotions("trustDisgust"), 0),
                       Math.max(emotions("fearAnger"), 0),
                       Math.min(emotions("fearAnger"), 0),
                       Math.max(emotions("surpriseAnticipation"), 0),
                       Math.min(emotions("surpriseAnticipation"), 0)
                   ).zip(List(
                        0.01283263426811265,
                        -0.015167768229712959,
                        -0.008816686431890883,
                        -0.010397153790698947,
                        -0.03596629349186481,
                        0.04720398433849748,
                        0.017492944745416433,
                        -0.002131584981046307
                   )).foldLeft(0.0)((a,b) => a + (b._1 * b._2))
                ) +
                persuasionScore(tokens, tags, List(
                    -0.04410027564895524,
                    0.006936658261575759,
                    0.06878315455821574,
                    -0.02936764697622178,
                    0.011511011885622124,
                    0.07180940812836727,
                    0.016855596735197393,
                    -0.0647607629115718
                ), language)
        )
    }
}