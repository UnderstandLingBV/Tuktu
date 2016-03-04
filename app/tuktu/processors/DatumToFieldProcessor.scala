package tuktu.processors

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api._

/**
 * Moves a datum to a field
 */
class DatumToFieldProcessor(resultName: String) extends BaseProcessor(resultName) {

    override def initialize(config: JsObject) {}

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket( data.data.map( datum => Map(resultName -> datum)) ) 
    })
}