package tuktu.nlp.processors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import play.api.libs.json.Json
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import scala.util.Try
import play.api.libs.json.JsValue

/**
 * Performs location detection
 * 
 * Uses cities15000.zip from http://download.geonames.org/export/dump/ (unpack before use)
 * Uses countries.json from https://raw.githubusercontent.com/mledoze/countries/master/countries.json
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
            
            val location = getLocation(tokens)
            if(location.isDefined) {
                datum + (resultName -> Array(location.get.lat, location.get.long))
            } else
                datum
        }
    })
}

object LocationDetectionProcessor {
    import play.api.Play
    
    // the maximum amount of tokens to look at when trying to determine a location
    val maxTokenSize = 3
    
    // Helper case class to store some location information
    case class LocationInformation(lat: Float, long: Float, pop: Int = 0) 
    
    val (asciiCities, utf8Cities, altCities) = {
        val asciiCities = collection.mutable.Map.empty[String, LocationInformation]
        val utf8Cities = collection.mutable.Map.empty[String, LocationInformation]
        val altCities = collection.mutable.Map.empty[String, LocationInformation]
        val f = scala.io.Source.fromFile(Play.current.configuration.getString("tuktu.nlp.location.city15000").getOrElse(""))("utf8")
        f.getLines.map(_.toLowerCase).foreach( line => {
            val splitted = line.split("\t")
            val info = LocationInformation(splitted(4).toFloat, splitted(5).toFloat,splitted(14).toInt)
            
            if(!utf8Cities.contains(splitted(1)) || utf8Cities(splitted(1)).pop < info.pop) {
                utf8Cities += splitted(1) -> info
            } 
            
            if(!asciiCities.contains(splitted(2)) || asciiCities(splitted(2)).pop < info.pop) {
                asciiCities += splitted(2) -> info
            } 
            // added a filter for the alt names, because it would give too many false positive
            // e.g. WAS for Washington
            splitted(3).split(",").filter(_.size > 4).foreach(alt => 
                if(!altCities.contains(alt) || altCities(alt).pop < info.pop)
                    altCities += alt -> info    
            )
        })
        
        f.close
        
        (asciiCities.toMap, utf8Cities.toMap, altCities.toMap)
    }

    val countries = {
        val countries = collection.mutable.Map.empty[String, LocationInformation]
        val f = scala.io.Source.fromFile(Play.current.configuration.getString("tuktu.nlp.location.countries").getOrElse(""))("utf8")
        val json = Json.parse(f.getLines.mkString("\n")).as[List[JsObject]]
        json.foreach { x =>
            {
                Try {
                    val latlng = (x \ "latlng").as[List[BigDecimal]]
                    val locInfo = LocationInformation(latlng(0).floatValue, latlng(1).floatValue)

                    val names = collection.mutable.ListBuffer.empty[JsValue]
                    names += (x \ "name" \ "common")                    
                    names ++= x \ "translations" \\ "common"
                    
                    names.map (_.as[String].toLowerCase).foreach { x=> countries += x -> locInfo }
                      
                }

            }
        }
        
        f.close
        
        countries.toMap
    }
    
    // Get the location mentioned in the tokens
    // First Country then cities
    def getLocation(tokens: Seq[String], sliding: Int = maxTokenSize): Option[LocationInformation] = {
        if (sliding < 1 ) getCity(tokens)
        else {
            val slice = tokens.sliding(sliding).map(_.mkString(" ")).toSeq
            val result = slice.collectFirst { case i if(countries.contains(i)) => countries(i) }
            if (result.isDefined) result
            else getLocation(tokens, sliding - 1)            
        }
    }
    
    // Looks for the most likely city, based on population size
    def getCity(tokens: Seq[String], sliding: Int = maxTokenSize): Option[LocationInformation] = {
        if (sliding < 1 ) None
        else {
            val slice = tokens.sliding(sliding).map(_.mkString(" ")).toSeq
            
            val result = List(
                slice.collectFirst { case i if(asciiCities.contains(i)) => asciiCities(i) },
                slice.collectFirst { case i if(utf8Cities.contains(i)) => utf8Cities(i) },
                slice.collectFirst { case i if(altCities.contains(i)) => altCities(i) }
            ).flatten.headOption
            
            if(result.isDefined) result
            else getCity(tokens, sliding - 1)
        }
    }
}