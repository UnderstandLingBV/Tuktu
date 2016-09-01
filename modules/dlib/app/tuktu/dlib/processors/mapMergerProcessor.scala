package tuktu.dlib.processors

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.api.BaseProcessor
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import scala.annotation.migration

/**
 * Merges two JSON objects.
 */
class MapMergerProcessor (resultName: String) extends BaseProcessor(resultName) 
{
   var map1: String = _
   var map2: String = _
   var priority: Option[String] = _
    
    override def initialize(config: JsObject) 
    {
        // Get the name of the field containing the first map to merge        
        map1 = (config \ "map1").as[String]
        // Get the name of the field containing the second map to merge        
        map2 = (config \ "map2").as[String]
        // What field has the priority in case of conflict
        priority = (config \ "priority").as[Option[String]]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      DataPacket(for (datum <- data.data) yield {
                val m1: JsObject = datum( map1 ).asInstanceOf[JsObject]
                val m2: JsObject = datum( map2 ).asInstanceOf[JsObject]
                val result: JsObject = priority match{
                    case None => combine( m1, m2, m1.as[Map[String,JsValue]].keys ++ m2.as[Map[String,JsValue]].keys )
                    case Some( p ) => p match{
                        case "map1" => complete( m1, m2 )
                        case "map2" => complete( m2, m1 )
                        case _ => combine( m1, m2, m1.as[Map[String,JsValue]].keys ++ m2.as[Map[String,JsValue]].keys )
                    }
                }        
                datum + ( resultName -> result )
            })
    })
    
    // complete m1 with missing keys from m2 (m2 keys already in m1 are ignored)
    def complete( m1: JsObject, m2: JsObject ): JsObject =
    {
        return m1 ++ m2
    }
    
    // merge m1 and m2
    def combine( m1: JsObject, m2: JsObject, keys: Iterable[String] ): JsObject =
    {
        val key = keys.head
        if ( keys.tail.isEmpty )
        {
            return Json.obj( key -> mergeKey( (m1 \ key).asOpt[JsValue], (m2 \ key).asOpt[JsValue] ) )
        }
        else
        {
            return combine( m1, m2, keys.tail ) ++ Json.obj( key -> mergeKey( (m1 \ key).asOpt[JsValue], (m2 \ key).asOpt[JsValue] ) )
        }
    }
    
    def mergeKey( val1: Option[JsValue], val2: Option[JsValue] ): JsValue =
    {
        (val1,val2) match
        {
            case (None,None) => JsNull
            case (None, Some(v2)) => v2
            case (Some(v1), None) => v1
            case ( Some(v1), Some(v2) ) => (v1,v2) match
            {
                case ( a: JsArray, b: JsArray ) => a ++ b
                case ( a: JsObject, b: JsObject ) => combine( a, b, a.as[Map[String,JsValue]].keys ++ b.as[Map[String,JsValue]].keys )
                case ( a: JsValue, b: JsValue ) => Json.arr( a, b )
            }
        }    
    }

}