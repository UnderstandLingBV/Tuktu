package tuktu.processors.bucket

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.DataPacket

/**
 * Groups data in the specified field.
 */
class GroupByProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var field = ""
    
    override def initialize(config: JsObject) = {
        // Get the field to group on
        field = (config \ "field").as[String]
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {     
        val grouped = data.groupBy(_(field))
        var groupedResult = collection.mutable.Map[String, Any]()
        
        List((for (group <- grouped) yield {
            (group._1 match {
                case g: String => g
                case _ => ""
            }) -> group._2
        }).toMap)        
    }

}