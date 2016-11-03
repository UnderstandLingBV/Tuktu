package tuktu.dlib.processors

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import tuktu.api._
import tuktu.api.BaseProcessor

/**
 * Increments a field integer value by 1 starting from an initial value.
 */
class IncrementerProcessor (resultName: String) extends BaseProcessor(resultName) 
{
   var initial: Int = _
    
    override def initialize(config: JsObject) 
    {
        // Initial value        
        initial = (config \ "initial").asOpt[Int].getOrElse(0)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
      new DataPacket(for (datum <- data.data) yield {
                initial += 1
                datum + ( resultName -> initial )
            })
    })    
}