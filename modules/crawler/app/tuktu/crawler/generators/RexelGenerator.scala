package tuktu.crawler.generators

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
import scala.util.Random

case class RexelResultPacket(
        url: String,
        name: String,
        ean: String,
        price: String
)

case class SubcategoryPacket(
        url: String
)

/**
 * Actor that gets content from a Rexel category page
 */
class RexelScraperActor(parent: ActorRef, categoryUrl: String) extends Actor with ActorLogging {
    // Private web client
    val webClient = new WebClient
    org.apache.log4j.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(org.apache.log4j.Level.OFF)
    java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.OFF)
    
    // Open category page and look for subcategories
    val categoryContent: HtmlPage = webClient.getPage(categoryUrl)
    val subcategories = categoryContent.getByXPath("//li[@id='marker-catStart']//a")
        .asInstanceOf[java.util.ArrayList[HtmlAnchor]].asScala.map(link => link.getHrefAttribute).toList
    // Visit each subcategory separately
    subcategories.foreach(subcat => {
        self ! new SubcategoryPacket("https://www.rexel.nl" + subcat)
    })
    
    def receive() = {
        case sp: SubcategoryPacket => {
            
        }
        case sp: StopPacket => {
            webClient.close()
            self ! PoisonPill
        }
    }
}

/**
 * Gets products off the Rexel page
 */
class RexelGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends BaseGenerator(resultName, processors, senderActor) {
    // All categories
    val categories = List(
            "https://www.rexel.nl/kabel/category/11",
            "https://www.rexel.nl/kabelmanagement/category/18",
            "https://www.rexel.nl/verlichting/category/21",
            "https://www.rexel.nl/schakelmateriaal/category/26",
            "https://www.rexel.nl/installatie/category/31",
            "https://www.rexel.nl/industrie-mro/category/41",
            "https://www.rexel.nl/datacom-beveiliging/category/51",
            "https://www.rexel.nl/gereedschap-pbm/category/61",
            "https://www.rexel.nl/duurzame-energie/category/71"
    )
    val scrapers = collection.mutable.ListBuffer.empty[ActorRef]
   
    override def receive() = {
         case config: JsValue => {
             // Set up scraping actors and start
             categories.foreach(cat => {
                 val actor = Akka.system.actorOf(Props(classOf[RexelScraperActor], self, cat))
                 scrapers += actor
             })
         }
         case rrp: RexelResultPacket => {
             // Push text forward
             channel.push(new DataPacket(List(Map(
                     resultName -> Map(
                             "url" -> rrp.url,
                             "name" -> rrp.name,
                             "ean" -> rrp.ean,
                             "price" -> rrp.price
                     )
             ))))
         }
         case sp: StopPacket => {
             scrapers.foreach(a => a ! new StopPacket)
             cleanup
         }
         case ip: InitPacket => setup
     }
}