package tuktu.nlp.models

import tuktu.ml.models.BaseModel
import com.github.jfasttext.JFastText
import scala.collection.JavaConverters._
import scala.util.Random
import java.util.Date

class URBEM(language: String, jft: JFastText) extends BaseModel {
    val seedWords = collection.mutable.Map.empty[String, List[Array[java.lang.Float]]]
    val leftFlips = collection.mutable.ArrayBuffer.empty[Array[java.lang.Float]]
    val rightFlips = collection.mutable.ArrayBuffer.empty[Array[java.lang.Float]]
    
    /**
     * Initializes the seed words per class, the left flips and right flips
     * @param seedWords The seed words. The key is the class name for which these
     * seed words hold. The value is a list of words or phrases.
     * @param leftFlips A list of left-flip words/phrases that negate the emissions to the left
     * @param rightFlips A list of word/phrase negations to the right
     */
    def setSeedWords(
            seedWords: Map[String, List[List[String]]],
            leftFlips: List[List[String]], rightFlips: List[List[String]]
    ) {
        this.seedWords.clear
        this.leftFlips.clear
        this.rightFlips.clear
        this.seedWords ++= seedWords.map {wordGroup =>
            wordGroup._1 -> wordGroup._2.map {words =>
                if (words.size == 1) jft.getVector(words.head).asScala.toArray
            else Array.empty[java.lang.Float] // Wait for support for sentences
            }
        }
        this.leftFlips ++= leftFlips.map {words =>
            if (words.size == 1) jft.getVector(words.head).asScala.toArray
            else Array.empty[java.lang.Float] // Wait for support for sentences
        }
        this.rightFlips ++= rightFlips.map {words =>
            if (words.size == 1) jft.getVector(words.head).asScala.toArray
            else Array.empty[java.lang.Float] // Wait for support for sentences
        }
    }
    
    def predict(document: String, seedCutoff: Double, negationCutoff: Double) = {
        // Get sentences from this document
        NLP.getSentences(document, language).flatMap {sentence =>
            val tokens = sentence.toLowerCase.split(" ").toList
            val words = tokens.map {token =>
                jft.getVector(token).asScala.toArray
            }
            
            // Set up the emissions for each class
            val emissions = seedWords.keys.map {label =>
                val em = collection.mutable.ArrayBuffer.empty[Double]
                (0 to words.size - 1).foreach(_ => em += 0.0)
                label -> em
            } toMap
            
            val flaggedWords = collection.mutable.Map.empty[Int, Boolean].withDefaultValue(false)
            
            words.zipWithIndex.foreach {sentenceWord =>
                val wordVector = sentenceWord._1
                val wordOffset = sentenceWord._2
                // Process the seed words per class
                seedWords.keySet.foreach {label =>
                    val classSeedWords = seedWords(label)
                    // Find a matching seed word
                    classSeedWords.zipWithIndex.find {seedWord =>
                        val sw = seedWord._1
                        val seedWordOffset = seedWord._2
                        CosineSimilarity.cosineSimilarity(wordVector, sw) >= seedCutoff
                    } map {seedWord =>
                        // Found a word that matches one of the seed words enough, update emissions
                        (0 to Math.max(wordOffset, words.size - 1 - wordOffset)).foreach {dist =>
                            // Go to the left and to the right
                            List(wordOffset - dist, wordOffset + dist)
                                .distinct.filter(idx => idx >= 0 && idx < words.size)
                                .foreach {idx =>
                                    // Update emission
                                    emissions(label)(idx) += Math.exp(-dist)
                                }
                        }
                    }
                }
            }
            
            // Now negate to the right first
            words.zipWithIndex.foreach {sentenceWord =>
                val word = sentenceWord._1
                val wordOffset = sentenceWord._2
                
                rightFlips.find {seedVector =>
                    CosineSimilarity.cosineSimilarity(word, seedVector) >= negationCutoff
                } map {flipWord =>
                    // Negate stuff to the right
                    (0 to words.size - 1 - wordOffset).foreach {dist =>
                        // Update emission
                        emissions.keys.foreach {label =>
                            emissions(label)(wordOffset + dist) *= -1
                        }
                    }
                }
            }
            
            // And now to the left
            words.zipWithIndex.foreach {sentenceWord =>
                val word = sentenceWord._1
                val wordOffset = sentenceWord._2
                
                rightFlips.find {seedVector =>
                    CosineSimilarity.cosineSimilarity(word, seedVector) >= negationCutoff
                } map {flipWord =>
                    // Negate stuff to the left
                    (0 to wordOffset).foreach {dist =>
                        // Update emission
                        emissions.keys.foreach {label =>
                            emissions(label)(wordOffset - dist) *= -1
                        }
                    }
                }
            }
            
            // Return the emission
            emissions.toList.map {em =>
                em._1 -> em._2.sum
            }
        } groupBy {_._1} map {score =>
            score._1 -> score._2.foldLeft(0.0)((a,b) => a + b._2)
        }
    }
}

object TestURBEM {
    def main(args: Array[String]) {
        val jft = new JFastText
        jft.loadModel("D:/Erik/Prive/Tuktu/Sentiment/emotions.bin")

        val ur = new URBEM("nl", jft)
        ur.setSeedWords(
            Map(
                "positive" -> List(
                    "aanbevelenswaardig","aanbiddelijk","aandachtig","aangelegd","aangenaam","aanlokkelijk","aanrader","aanstekelijk","aantrekkelijk","aardig","adembenemend","adequaat","afbreekbaar","allerbest","allerleukst","allermooist","altruïstisch","ambitieus","amusant","antiracistisch","apetrots","attractief","authentiek","avontuurlijk","baanbrekend","bedachtzaam","beeldschoon","befaamd","begaafd","begeerlijk","begeesterd","begenadigd","behendig","bekeken","belangeloos","belangwekkend","beleefd","beloftevol","benodigd","beroemd","best","betrouwbaar","bevallig","bevredigend","bewonderenswaardig","bezienswaardig","bijzonder","blij","blijmoedig","bloedmooi","bloedstollend","boeiend","branderig","briljant","broodnuchter","buitengewoon","capabel","charismatisch","charitatief","chic","clever","close","cool","copieus","correct","coulant","crazy","creatief","dankbaar","dapper","degelijk","diep","diepgravend","diepzinnig","dierbaar","doeltreffend","dol","dolblij","dolenthousiast","dolgelukkig","doordacht","doorgewinterd","doorwrocht","duidelijk","duizelingwekkend","eclatant","eenvoudig","eerlijk","eersteklas","eersterangs","effectief","efficiënt","eindeloos","elegant","energiek","enig","enorm","enthousiast","erotisch","erudiet","ervaren","evenwichtig","exquis","extatisch","fabelachtig","fabuleus","fair","fameus","fantasierijk","fantasievol","fantastisch","fascinerend","favoriet","feest","feestelijk","feeëriek","feilloos","fenomenaal","ferm","fijn","filantropisch","fit","fleurig","flink","florissant","formidabel","fortuinlijk","fraai","fris","fruitig","functioneel","gaaf","geacht","gebruikersvriendelijk","gedegen","gedenkwaardig","geestdriftig","geestig","geinig","gek","geknipt","gelauwerd","geliefd","geliefde","gelijkmatig","gelukkig","gelukzalig","gemakkelijk","geniaal","genoeglijk","genot","gepassioneerd","geraffineerd","gerechtigd","gereputeerd","gerespecteerd","geroutineerd","geschikt","geslaagd","getalenteerd","getrouw","geweldig","gezellig","gezind","gezond","giechelig","gis","glansrijk","glorierijk","glorieus","goddelijk","godvruchtig","goed","goede","goedmoedig","goeie","gouden","grandioos","grappig","gretig","grondig","groot","groots","hallucinant","handig","happig","harmonieus","hartelijk","hartig","hartstochtelijk","hartverwarmend","heerlijk","helder","heldhaftig","hemels","hilarisch","hongerig","hooggeplaatst","hoogstaand","hoogwaardig","hoopgevend","hoopvol","huiselijk","huishoudelijk","humaan","humoristisch","humorvol","hypermodern","ideaal","idealistisch","ideëel","ijzersterk","illuster","imposant","impressief","indringend","indrukwekkend","infrastructureel","ingenieus","inspirerend","instructief","instrumenteel","integer","intelligent","interessant","intrigerend","inventief","invloedrijk","juist","juweeltje","kant-en-klaar","keigoed","keiig","kerngezond","kien","klaar","klaarlicht","kleurrijk","knap","knus","koddig","koket","komisch","kostbaar","kostelijk","krachtdadig","krachtig","krankjorum","kriebelig","kristalhelder","kunstig","kunstminnend","kwiek","lankmoedig","lauw","leerzaam","legendarisch","leidinggevend","lekker","leuk","levendig","levenslustig","lezenswaardig","lief","liefdadig","liefdevol","lollig","loos","loyaal","lumineus","lux","luxe","luxueus","machtig","macrobiotisch","magisch","magistraal","magnifiek","makkelijk","manhaftig","manmoedig","mededeelzaam","meeslepend","meesterlijk","meesterwerk","memorabel","micro-economisch","mieters","miraculeus","moedig","mooi","moppig","natuurlijk","net","netjes","nieuwsgierig","nobel","nuttig","oersterk","onaantastbaar","onderhoudend","onevenaarbaar","onfeilbaar","ongekend","ongelofelijk","ongelofeloos","ongevaarlijk","ongeëvenaard","onkwetsbaar","onnadrukkelijk","onovertroffen","onoverwinnelijk","onschadelijk","onschuldig","onsterfelijk","ontroerend","ontspannen","ontspannend","ontzagwekkend","onuitputtelijk","onvergetelijk","onvolprezen","onwaarschijnlijk","onweerstaanbaar","onwijs","open","openhartig","opgelucht","oprecht","optimaal","optimistisch","opvallend","opwekkend","opwindend","opzienbarend","origineel","overgelukkig","overheerlijk","overtuigend","overwinning","overzichtelijk","pasklaar","perfect","piekfijn","pienter","pikant","pittig","plezierig","populair","positief","prachtig","praktisch","prettig","prima","probaat","professioneel","profetisch","puik","rationeel","razend","realiseerbaar","relaxed","respectabel","respectvol","reuze","riant","rijk","roemrijk","roemrucht","roemruchtig","roemvol","romantisch","rooskleurig","ruimhartig","schatrijk","scherp","scherpzinnig","schilderachtig","schitterend","schoon","selfmade","sensationeel","sexy","sfeervol","slim","smaakvol","smachtend","smakelijk","smeuïg","smoorverliefd","snel","snugger","sociaal","soepel","solide","somptueus","speciaal","spectaculair","speels","speltechnisch","spiernaakt","spits","spitsvondig","spontaan","sprookjesachtig","steengoed","steenrijk","sterk","stevig","stijlvol","stout","stoutmoedig","straf","strijdvaardig","subliem","succesrijk","succesvol","super","supergoed","superieur","superleuk","supermooi","supersnel","superspannend","superspannende","sympathiek","taai","tactvol","talentvol","teder","tochtig","tof","toonaangevend","top","trefzeker","trots","trouw","trouwhartig","uitgelezen","uitmuntend","uitstekend","uitzonderlijk","uniek","vaardig","vakbekwaam","vakkundig","vastbesloten","veelbelovend","veelbewogen","veelgelezen","veelgeprezen","verbazingwekkend","verblindend","verbluffend","verfrissend","verhandelbaar","verheven","verlekkerd","verliefd","verliezer","vermaard","vermakelijk","vermetel","vernuftig","verrassend","verrukkelijk","verstandig","vertrouwd","vindingrijk","virtuoos","visrijk","vlekkeloos","vloeiend","vlot","voedzaam","volleerd","volmaakt","volwaardig","vooraanstaand","voorspoedig","voortreffelijk","vriendelijk","vrij","vrolijk","vurig","waardevol","waardig","waarheidsgetrouw","wakker","warm","wauw","weelderig","weergaloos","weldenkend","weldoordacht","weledelzeergeleerd","welgeschapen","welkom","wereldberoemd","wereldvermaard","werkzaam","wervelend","wijs","wild","wilskrachtig","winnaar","wonderbaar","wonderbaarlijk","wonderlijk","wonderschoon","wreed","wulps","zacht","zachtaardig","zalig","zinnig","zinvol","zomers","zondags","zonnig","zorgzaam","zwoel"
                ).map(a => List(a)),
                "negative" -> List(
                    "aangebrand","aanmatigend","aanstellerig","aartslui","abnormaal","abominabel","achteloos","achterdochtig","afgedraaid","afgezaagd","afgrijselijk","afgunstig","afknapper","afschuwelijk","afschuwwekkend","afstandelijk","afstotelijk","agressief","akelig","alledaags","amateuristisch","anaal","angstaanjagend","angstig","angstwekkend","apathisch","apocalyptisch","arbeidsongeschikt","arglistig","arm","armetierig","armoedig","armzalig","arrogant","asociaal","astmatisch","babbelziek","banaal","bang","bar","barbaars","bars","bazig","bedlegerig","bedroefd","beestachtig","behoeftig","bekakt","beklagenswaardig","beklemmend","bekocht","bekrompen","belabberd","belachelijk","belazerd","belerend","bemoeiziek","benard","benauwd","beroerd","berooid","berouwvol","beschamend","bescheiden","beschonken","besmettelijk","besodemieterd","bespottelijk","beteuterd","bezeten","bijgelovig","bitter","blasfemisch","bloederig","bloedig","boers","bombastisch","boos","brandend","broeierig","broos","bruusk","bruut","buiig","catastrofaal","chagrijnig","chaotisch","charlatan","charlatanisme","chauvinistisch","cholerisch","claustrofobisch","clichématig","confronterend","controversieel","crimineel","cru","cynisch","decadent","deerlijk","defaitistisch","dement","deplorabel","depressief","derdegraads","derderangs","desastreus","destructief","diabolisch","dik","dilettanterig","dissonant","dodelijk","dogmatisch","doldriest","dom","donker","doodgeboren","doodkalm","doodmoe","doodsbleek","doodziek","doordeweeks","draconisch","drakerig","dramatisch","driest","droef","droevig","druilerig","dubbelhartig","duivels","duur","dwangmatig","dweperig","dyslectisch","edel","eentonig","eenzaam","eenzijdig","eerloos","eerzuchtig","egocentrisch","egoïstisch","ellendig","enerverend","eng","epidemisch","erbarmelijk","erg","ergerlijk","ergerniswekkend","exhibitionistisch","extreem-links","extremistisch","faliekant","fataal","fiasco","flauw","flop","flut","fnuikend","fout","foutief","frauduleus","frustratie","funest","furieus","gedesillusioneerd","gedoemd","gehaaid","gehandicapt","gek","gekmakend","gekrenkt","gekunsteld","gemaakt","gemakzuchtig","gemeen","genadeloos","gepantserd","gering","gespannen","gevaarlijk","geweldadig","gewetenloos","gewraakt","giftig","godsgruwelijk","godsjammerlijk","godverdoms","godverlaten","goedkoop","goor","gortdroog","gratuit","grieperig","griezelig","grimmig","grof","grotesk","gruwel","gruwelijk","gruwzaam","gênant","hachelijk","halfbakken","halfhartig","hallucinant","halsstarrig","hanig","hardhandig","hardleers","hardvochtig","hatelijk","hautain","heilig","heilloos","hersenloos","hinderlijk","honds","hoogdravend","hooghartig","hoogmoedig","hopeloos","hovaardig","huiverig","huiveringwekkend","hulpeloos","hyperactief","hypocriet","idioot","idioten","ijselijk","ijskoud","ijzig","ijzingwekkend","illegaal","immoreel","impertinent","incoherent","inconsequent","incorrect","ineffectief","infantiel","inferieur","intolerant","irrationeel","irritant","jaloers","jammer","jammerlijk","kankerverwekkend","ketters","kil","kinderachtig","kinderloos","klaaglijk","klef","klein","kleinburgerlijk","kleinzerig","kleinzielig","kleurloos","klimatologisch","kloek","klote","klucht","klungelig","knoeierig","knullig","koel","koeltjes","kolderiek","koloniaal","kommerlijk","kommervol","kortzichtig","koud","krakkemikkig","krampachtig","krankzinnig","kregelig","krom","kwaad","kwaadaardig","kwalijk","laag","laag-bij-de-gronds","laaghartig","laakbaar","labiel","ladderzat","laf","lafhartig","lamentabel","langdradig","lasterlijk","lastig","leed","leeg","lelijk","leugenaars","leugenachtig","levensgevaarlijk","lichtzinnig","liefdeloos","lijvig","links","listig","lodderig","lomp","loos","losers","luguber","lui","lullig","macaber","machiavellistisch","machteloos","mager","malafide","margi's","masochistisch","mat","matig","meedogenloos","melodramatisch","mensonterend","mensonwaardig","meteorologisch","middeleeuws","middelmatig","miezerig","militant","min","mis","miserabel","mislukkeling","misplaatst","misprijzend","misselijk","mistroostig","moe","moeizaam","morbide","muf","muffig","murw","naar","naargeestig","nadelig","narcistisch","narrig","navrant","naïef","nefast","negatief","neonazistisch","nerveus","neurotisch","nietszeggend","nodeloos","nonchalant","noodlottig","nors","nutteloos","obscuur","obsessief","occult","oenig","oersaai","oliedom","omineus","omslachtig","onaangenaam","onaangepast","onaantrekkelijk","onaanzienlijk","onaardig","onacceptabel","onbarmhartig","onbegrepen","onbegrijpelijk","onbeholpen","onbehoorlijk","onbeleefd","onbenullig","onbenut","onbeschoft","onbetamelijk","onbetrouwbaar","onbevattelijk","onbevredigd","onbevredigend","onbewogen","onbezonnen","onbuigzaam","oncomfortabel","ondermaats","onderontwikkeld","ondervoed","ondoorgrondelijk","ondoorzichtig","ondrinkbaar","oneerlijk","onervaren","onevenwichtig","onfatsoenlijk","ongedisciplineerd","ongeloofwaardig","ongelovig","ongelukkig","ongemakkelijk","ongeneeslijk","ongepast","ongeschikt","ongezond","ongeïnspireerd","onhandig","onhebbelijk","onheilspellend","onhoudbaar","onhygiënisch","oninteressant","onjuist","onkuis","onleefbaar","onleesbaar","onmachtig","onmenselijk","onmogelijk","onnaspeurbaar","onnaspeurlijk","onnatuurlijk","onnozel","onopvallend","onoverbrugbaar","onoverzichtelijk","onpeilbaar","onpersoonlijk","onplezierig","onprettig","onrechtvaardig","onrustig","onsamenhangend","onsmakelijk","onsportief","onsympathiek","onthand","onthutst","ontvlambaar","ontzet","onuitroeibaar","onuitstaanbaar","onveilig","onverantwoord","onverantwoordelijk","onvergeeflijk","onverkwikkelijk","onverstandig","onvertogen","onverzettelijk","onvolwassen","onvoorzichtig","onvriendelijk","onwelkom","onwerkbaar","onwetend","onwijs","onwrikbaar","onzalig","onzedelijk","onzindelijk","onzinnig","onzuiver","oppervlakkig","opportunistisch","opvliegend","ordinair","overbodig","overhaast","paniekerig","paranoïde","paternalistisch","pathetisch","penibel","perkamentachtig","pervers","pessimistisch","petieterig","pijnlijk","pissig","plat","platvloers","pover","praatziek","precair","pretentieus","provinciaal","psychopathisch","puberaal","racist","racistisch","rampspoedig","rampzalig","rancuneus","ranzig","rasperig","razend","recalcitrant","repressief","respectloos","restrictief","ridicuul","rigide","roekeloos","roemloos","roestkleurig","rot","rustiek","ruw","rücksichtslos","saai","sadistisch","satanisch","schaamteloos","schadelijk","schandalig","schandelijk","scheef","scherp","schimmelig","schimmig","schizofreen","schlemielig","schonkig","schriel","schrijnend","schrikbarend","schuin","schuins","schuldig","schunnig","schuw","seksistisch","sektarisch","sfeerloos","shabby","simpel","simplistisch","sinister","sip","slaafs","slaapverwekkend","slecht","sloom","slopend","slordig","smadelijk","smakeloos","smartelijk","smerig","snikheet","snipverkouden","snood","somber","spichtig","spijtig","spookachtig","spotziek","staatsgevaarlijk","stiefmoederlijk","stijlloos","stoethaspelig","stom","stompzinnig","storend","streng","strikt","stringent","stroef","stroperig","stumperig","stuntelig","stuurs","suf","suffig","sukkelig","sullig","superieur","suïcidaal","taai","teleurstellend","temperamentvol","tergend","terminaal","terroristisch","tevergeefs","tjevenstreken","toornig","toxisch","tragisch","traumatisch","treurig","triest","troosteloos","trots","trouweloos","truttig","tuttig","tweederangs","tweederangskandidaat","twistziek","uitgekookt","uitzichtloos","vals","verbaal","verbolgen","verderfelijk","verdrietig","vergeefs","vergezocht","verkeerd","verkeken","vermoeid","verschrikkelijk","versuft","vervangbaar","vervelend","verwarrend","verwoestend","verzengend","vettig","vies","vijandelijk","voorspelbaar","vreemd","vreselijk","vrijpostig","vuil","vulgair","vuurgevaarlijk","waanwijs","waanzinnig","waardeloos","walgelijk","wanhopig","wankel","wantrouwend","weerbarstig","weerzinwekkend","weigerachtig","wereldschokkend","werkloos","wild","willoos","wispelturig","wit","woedend","woelig","woordblind","wormstekig","wraakzuchtig","wreed","wreedaardig","zat","zeer","zenuwziek","zeurderig","zever","zeveren","ziek","ziekelijk","zielig","zinloos","zoetsappig","zondig","zootje","zuur","zuurstofarm","zwaargewond","zwak","zwakbegaafd","zwart","zwartgallig","zweverig"
                 ).map(a => List(a))
            ),
            List(
                List("niet"), List("geen"), List("noch"), List("nooit")
            ),
            List(
                List("maar"), List("desalniettemin"), List("doch"), List("alhoewel")
            )
        )
        
        /*val testCases = List(
            ("Dit is een uitmuntende test , maar toch best vervelend .", "negative"),
            ("Dit is een test .", "neutral"),
            ("Dit is een gewone test .", "neutral"),
            ("Dit is een goed test .", "positive"),
            ("Dit is een goede test .", "positive"),
            ("Dit is een slechte test .", "negative"),
            ("Dit is een magnifieke test .", "positive"),
            ("Dit is een bedroevende test .", "negative"),
            ("Dit is een niet goede test .", "negative"),
            ("Dit is een niet slechte test .", "positive"),
            ("Dit is een goede test , maar toch best slecht .", "negative"),
            ("Dit is niet een goede test , maar geen slechte test .", "positive")
        )*/
        
        // Load some manually annotated test cases
        val testCases = {
            val f = scala.io.Source.fromFile("D:/Erik/Prive/Tuktu/Tweets/RBEM_Train/polarity_nl.txt")("utf8")
           val lines = f.getLines.toList
           f.close
           val data = lines.map {line =>
               val split = line.split("\t")
               (split(1), split(0).replace("__label__", ""))
           } groupBy {_._2}
           data.toList.flatMap {d =>
               Random.shuffle(d._2).take(100)
           }
        }
        
        // Grid search for cutoff
        val accuracies = ((40 to 50).map {i =>
            val cutoff = 0.1 + (i.toDouble / 100.toDouble)
            println(new Date(System.currentTimeMillis) + " -- Validating cutoff " + cutoff)
            // Compute accuracy
            (cutoff, {
                val numRight = (testCases.map {testCase =>
                    val prediction = ur.predict(testCase._1, cutoff, 0.9)
                    testCase._2 == {
                        if ((prediction("positive") != 0 || prediction("negative") != 0) && prediction("positive") > prediction("negative")) "positive"
                        else if ((prediction("positive") != 0 || prediction("negative") != 0) && prediction("negative") > prediction("positive")) "negative"
                        else "neutral"
                    }
                } toList).filter(_ == true).size
                numRight.toDouble / testCases.size.toDouble
            })
        } toList).groupBy(_._2).toList.sortBy(_._1)(Ordering[Double].reverse).head._2
        
        // Find best scores
        accuracies.foreach {c =>
            println("Accuracy: " + c._2 + " -- cutoff: " + c._1)
        }
        
        // Get highest and show
        val cutoff = accuracies.sortBy(_._1)(Ordering[Double].reverse).head._1
        testCases.foreach {testCase =>
            val prediction = ur.predict(testCase._1, cutoff, 0.9)
            val label = {
                if ((prediction("positive") != 0 || prediction("negative") != 0) && prediction("positive") > prediction("negative")) "positive"
                else if ((prediction("positive") != 0 || prediction("negative") != 0) && prediction("negative") > prediction("positive")) "negative"
                else "neutral"
            }
            /*println(testCase)
            println(prediction)
            println(label)
            println*/
        }
    }
}