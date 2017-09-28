package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket

/**
 * Performs location detection
 * 
 * Uses cities15000.zip from http://download.geonames.org/export/dump/
 */
class LocationDetectionProcessor(resultName: String) extends BaseProcessor(resultName) {
    import LocationDetectionProcessor._
    
    var fieldName: String = _

    override def initialize(config: JsObject) {
        // Get fields
        fieldName = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {        
        for (datum <- data) yield {
            val tokens = (datum(fieldName) match {
                case s: Seq[String] => s
                case s: Array[String] => s.toSeq
                case s: Any => Seq(s.toString)
            }).map(_.toLowerCase)
            
            val location = getLocation(maxTokenSize, tokens)
            if(location.isDefined) {
                datum + (resultName -> Array(location.get.lat, location.get.long))
            } else
                datum
        }
    })
}

object LocationDetectionProcessor {
    import play.api.Play
    // the maximum amount of tokens to look at when trying to determine a city
    val maxTokenSize = 3
    
    // Helper case class to store some information
    case class CityInformation(lat: Float, long: Float, pop: Int) 
    
    val asciiLocations = collection.mutable.Map.empty[String, CityInformation]
    val utf8Locations = collection.mutable.Map.empty[String, CityInformation]
    val altLocations = collection.mutable.Map.empty[String, CityInformation]
    
    val f = scala.io.Source.fromFile(Play.current.configuration.getString("tuktu.nlp.location.city15000").getOrElse(""))("utf8")
    f.getLines.map(_.toLowerCase).foreach( line => {
        val splitted = line.split("\t")
        val info = CityInformation(splitted(4).toFloat, splitted(5).toFloat,splitted(14).toInt)
        
        if(!utf8Locations.contains(splitted(1)) || utf8Locations(splitted(1)).pop < info.pop) {
            utf8Locations += splitted(1) -> info
        } 
        
        if(!asciiLocations.contains(splitted(2)) || asciiLocations(splitted(2)).pop < info.pop) {
            asciiLocations += splitted(2) -> info
        } 
        // added a filter for the alt names, because it would give too many false positive
        // e.g. WAS for Washington
        splitted(3).split(",").filter(_.size > 4).foreach(alt => 
            if(!altLocations.contains(alt) || altLocations(alt).pop < info.pop)
                altLocations += alt -> info    
        )
    })
    
    f.close
    
    // Looks for the most likely city, based on population size
    def getLocation(sliding: Int, tokens: Seq[String]): Option[CityInformation]  = {
        if (sliding < 1 ) None
        else {
            val slice = tokens.sliding(sliding).map(_.mkString(" ")).toSeq
            
            val result = List(
                slice.collectFirst { case i if(asciiLocations.contains(i)) => asciiLocations(i) },
                slice.collectFirst { case i if(utf8Locations.contains(i)) => utf8Locations(i) },
                slice.collectFirst { case i if(altLocations.contains(i)) => altLocations(i) }
            ).flatten.headOption
            
            if(result.isDefined) result
            else getLocation(sliding - 1, tokens)
        }
    }
}