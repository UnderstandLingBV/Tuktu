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
    // Helper case class to store some location information
    case class LocationInformation(lat: Float, lon: Float, pop: Int = 0)
    case class Center(lat: Float, lon: Float, radius: Float)
    
    var fieldName: String = _
    var asciiCities = Map.empty[String, LocationInformation]
    var utf8Cities = Map.empty[String, LocationInformation]
    var altCities = Map.empty[String, LocationInformation]
    var countries = Map.empty[String, LocationInformation]
    var centers = List.empty[Center]
    var maxWindow: Int = _

    override def initialize(config: JsObject) {
        // Get fields
        fieldName = (config \ "field").as[String]
        val countryFile = (config \ "country_file").as[String]
        val cityFile = (config \ "city_file").as[String]

        val x = {
            val asciiCities = collection.mutable.Map.empty[String, LocationInformation]
            val utf8Cities = collection.mutable.Map.empty[String, LocationInformation]
            val altCities = collection.mutable.Map.empty[String, LocationInformation]
            val f = scala.io.Source.fromFile(cityFile)("utf8")
            f.getLines.map(_.toLowerCase).foreach(line => {
                val splitted = line.split("\t")
                val info = LocationInformation(splitted(4).toFloat, splitted(5).toFloat, splitted(14).toInt)

                if (!utf8Cities.contains(splitted(1)) || utf8Cities(splitted(1)).pop < info.pop) {
                    utf8Cities += splitted(1) -> info
                }

                if (!asciiCities.contains(splitted(2)) || asciiCities(splitted(2)).pop < info.pop) {
                    asciiCities += splitted(2) -> info
                }
                // added a filter for the alt names, because it would give too many false positive
                // e.g. WAS for Washington
                splitted(3).split(",").filter(_.size > 4).foreach(alt =>
                    if (!altCities.contains(alt) || altCities(alt).pop < info.pop)
                        altCities += alt -> info)
            })

            f.close

            (asciiCities.toMap, utf8Cities.toMap, altCities.toMap)
        }
        asciiCities = x._1
        utf8Cities = x._2
        altCities = x._3

        countries = {
            val countries = collection.mutable.Map.empty[String, LocationInformation]
            val f = scala.io.Source.fromFile(countryFile)("utf8")
            val json = Json.parse(f.getLines.mkString("")).as[List[JsObject]]
            json.foreach { x =>
                {
                    Try {
                        val latlng = (x \ "latlng").as[List[BigDecimal]]
                        val locInfo = LocationInformation(latlng(0).floatValue, latlng(1).floatValue)

                        val names = collection.mutable.ListBuffer.empty[JsValue]
                        names += (x \ "name" \ "common")
                        names ++= x \ "translations" \\ "common"
                        names ++= (x \ "altSpellings").as[List[JsValue]].filter {spell =>
                            spell.as[String] == "USA" || spell.as[String].size > 3
                        }

                        names.map(_.as[String].toLowerCase).foreach { x => countries += x -> locInfo }

                    }

                }
            }

            f.close

            countries.toMap
        }
        
        // Load centers
        centers = (config \ "centers").asOpt[List[JsObject]].getOrElse(Nil).map {center =>
            new Center((center \ "lat").as[Float], (center \ "lon").as[Float], (center \ "radius").as[Float])
        }
        
        maxWindow = (config \ "max_window").asOpt[Int].getOrElse(3)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        for (datum <- data) yield {
            val tokens = (datum(fieldName) match {
                case s: Seq[String]   => s
                case s: Array[String] => s.toSeq
                case s: Any           => s.toString.split(" ").toSeq
            }).map(_.toLowerCase)

            datum + (resultName -> {
                val locations = getLocations(tokens)
                
                // If centers are given, filter out locations not within them and the radius
                {
                    if (centers.isEmpty) locations
                    else {
                        locations.filter {loc =>
                            centers.exists {center =>
                                Math.sqrt(Math.pow(loc._2.lat - center.lat, 2) + Math.pow(loc._2.lon - center.lon, 2)) <= center.radius
                            }
                        }
                    }
                } map {loc =>
                    Map(
                            "lat" -> loc._2.lat,
                            "lon" -> loc._2.lon,
                            "name" -> loc._1
                    )
                }
            })
        }
    })


    // Gets the locations in this text
    def getLocations(tokens: Seq[String]): List[(String, LocationInformation)] = {
        val windows = (1 to maxWindow).toList.flatMap {window =>
            tokens.sliding(window).map(_.mkString(" "))
        }
        
        // Try out countries
        val matchedCountries = {
            val matches = windows.collect {
                case w if (countries.contains(w)) => (w, countries(w))
            }
            // Remove all subsumed ones
            matches.collect {
                case m if !matches.exists {n => n._1 != m._1 && n._1.contains(m._1)} => m
            }
        }
        
        // Try out cities
        val matchedCities = {
            val matches = windows.collect {
                case w if (asciiCities.contains(w)) => (w, asciiCities(w))
                case w if (utf8Cities.contains(w)) => (w, utf8Cities(w))
                case w if (altCities.contains(w)) => (w, altCities(w))
            }
            // Remove all subsumed ones
            matches.collect {
                case m if !matches.exists {n => n._1 != m._1 && n._1.contains(m._1)} => m
            }
        }
        
        // Remove all countries that have more fine-grained cities
        {
            val matches = matchedCountries ++ matchedCities
            // Remove all subsumed ones
            matches.collect {
                case m if !matches.exists {n => n._1 != m._1 && n._1.contains(m._1)} => m
            }
        }
    }
}