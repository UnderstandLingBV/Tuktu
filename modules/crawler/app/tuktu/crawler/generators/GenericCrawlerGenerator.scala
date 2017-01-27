package tuktu.crawler.generators

import tuktu.api.BaseGenerator
import play.api.libs.iteratee.Enumeratee
import akka.actor.ActorRef
import akka.actor.Props
import tuktu.api.DataPacket
import tuktu.api.StopPacket
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Akka
import com.gargoylesoftware.htmlunit.html.HtmlDivision
import com.gargoylesoftware.htmlunit.html.HtmlAnchor
import akka.actor.ActorLogging
import akka.actor.PoisonPill
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlPage
import scala.util.Random
import akka.actor.Actor
import com.gargoylesoftware.htmlunit.html.HtmlElement
import scala.collection.JavaConverters._
import play.api.Play.current

case class ScrapeResult(
        content: List[String]
)

class GenericScraperActor(parent: ActorRef, crawlPattern: String, linkPattern: String) extends Actor with ActorLogging {
    // Private web client
    val webClient = new WebClient
    org.apache.log4j.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(org.apache.log4j.Level.OFF)
    java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.OFF)
    
    val visitedLinks = collection.mutable.ArrayBuffer.empty[String]
    
    def receive() = {
        case url: String => {
            // Get the page
            val content: HtmlPage = webClient.getPage(url)
            
            // Get textual information from main content
            val text = content.getByXPath(crawlPattern).asInstanceOf[java.util.ArrayList[HtmlElement]].asScala.toList
                .map(_.getTextContent)
            // Get all links
            val links = content.getByXPath(linkPattern).asInstanceOf[java.util.ArrayList[HtmlAnchor]].asScala
            
            // Send to parent
            parent ! new ScrapeResult(text)
            links.filter(!visitedLinks.contains(_)).foreach {link =>
                val href = {
                    val h = link.getHrefAttribute
                    if (!h.startsWith("http") && !h.startsWith("www"))
                        url + h else h
                }
                visitedLinks += href
                self ! href
            }
            
            if (links.size == 0) {
                self ! new StopPacket
                parent ! new StopPacket
            }
        }
        case sp: StopPacket => {
            webClient.close()
            self ! PoisonPill
        }
    }
}

class GenericCrawlerGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    var flatten: Boolean = _
    
    override def _receive = {
        case config: JsValue => {
             // Get params
             val startUrl = (config \ "url").as[String]
             val linkPattern = (config \ "link_pattern").as[String]
             val crawlPattern = (config \ "crawl_patten").as[String]
             flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
             
             // Start the crawling
             val actor = Akka.system.actorOf(Props(classOf[GenericScraperActor], self, crawlPattern, linkPattern))
             actor ! startUrl
         }
         case sr: ScrapeResult => {
             // Push text forward
             channel.push(DataPacket({
                     if (flatten) sr.content.map {c =>
                         Map(resultName -> c)
                     } else List(Map(resultName -> sr.content))
             }))
         }
    }
}