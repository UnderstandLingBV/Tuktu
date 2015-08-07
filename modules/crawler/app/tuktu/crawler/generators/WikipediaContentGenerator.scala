package tuktu.nlp.generators

import tuktu.api.BaseGenerator
import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import tuktu.api.DataPacket
import play.api.libs.json.JsValue
import tuktu.api.InitPacket
import tuktu.api.StopPacket
import akka.actor.ActorLogging
import akka.actor.Actor
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.gargoylesoftware.htmlunit.html.HtmlDivision
import com.gargoylesoftware.htmlunit.html.HtmlAnchor
import akka.actor.Props
import play.api.libs.concurrent.Akka
import play.api.Play.current
import akka.actor.PoisonPill
import scala.collection.JavaConverters._

case class ScrapedPacket(
        text: String,
        word: String,
        links: List[String]
)

/**
 * Actor that gets content and links from a wikipedia page
 */
class ScraperActor(parent: ActorRef, language: String) extends Actor with ActorLogging {
    // Private web client
    val webClient = new WebClient
    
    def receive() = {
        case word: String => {
            // Get wikipedia page
            val url = "https://" + language + ".wikipedia.org" + word
            val content: HtmlPage = webClient.getPage(url)
            
            // Get textual information from main content
            val text = content.getByXPath("//*[@id='bodyContent']").asInstanceOf[java.util.ArrayList[HtmlDivision]].asScala.head.asText
            // Get all links
            val links = content.getByXPath("//*[@id='bodyContent']//a").asInstanceOf[java.util.ArrayList[HtmlAnchor]].asScala
            // Filter out only wikipedia links
            val filteredLinks = links.map(link => {
                link.getHrefAttribute.replaceAll("#.*", "")
            }).filter(link => {
                link.startsWith("/wiki") &&
                !link.contains("File:") &&
                !link.contains("Wikipedia:") &&
                !link.endsWith(".png") &&
                !link.endsWith(".gif") &&
                !link.endsWith(".jpg") &&
                !link.endsWith(".jpeg")
            }).toList
            
            // Send to parent
            parent ! new ScrapedPacket(text, word.drop("/wiki/".size), filteredLinks)
        }
        case sp: StopPacket => {
            webClient.close()
            self ! PoisonPill
        }
    }
}

/**
 * Scrapes wikipedia for a specific language and a couple of seed words to start from
 */
class WikipediaContentGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    // Keep track of all the words scraped so far
    val scrapedWords = collection.mutable.HashSet.empty[String]
    val scrapers = collection.mutable.ListBuffer.empty[ActorRef]
    var scraperOffset = 0
    var includeWord = false
    
    override def receive() = {
         case config: JsValue => {
             // What language to crawl?
             val language = (config \ "language").as[String]
             // Initial seed words
             val seedWords = (config \ "seed_words").as[List[String]].map(word => "/wiki/" + word)
             seedWords.foreach(word => scrapedWords += word)
             // Should we include the word or not?
             includeWord = (config \ "include_word").asOpt[Boolean].getOrElse(false)
             
             // Set up scraping actors and start
             (0 to seedWords.size - 1).foreach(i => {
                 val actor = Akka.system.actorOf(Props(classOf[ScraperActor], self, language))
                 actor ! seedWords(i)
                 scrapers += actor
             })
         }
         case sp: ScrapedPacket => {
             // Push text forward
             channel.push(new DataPacket(List(Map(
                     resultName -> (Map(
                             "content" -> sp.text
                     ) ++ {
                         if (includeWord) Map("word" -> sp.word)
                         else Map()
                     })
             ))))
             
             // Continue with links, if required
             sp.links.foreach(link => {
                 // Already scraped?
                 if (!scrapedWords.contains(link)) {
                     scrapers(scraperOffset) ! link
                     
                     scrapedWords += link
                     scraperOffset = (scraperOffset + 1) % scrapers.size
                 }
             })
         }
         case sp: StopPacket => {
             scrapers.foreach(a => a ! new StopPacket)
             cleanup
         }
         case ip: InitPacket => setup
     }
}