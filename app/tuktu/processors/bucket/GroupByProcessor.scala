package tuktu.processors.bucket

import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.DataPacket

/**
 * Groups data in the specified field.
 */
class GroupByProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var fields: List[String] = _
    var flatten = false
    
    override def initialize(config: JsObject) = {
        // Get the field to group on
        fields = (config \ "fields").as[List[String]]
        flatten = (config \ "flatten").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor
    
    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        var groupMap = collection.mutable.Map[String, Any]()
        
        // Build the map
        List(groupBuilder(data, fields))
    }
    
    def groupBuilder(data: List[Map[String, Any]], fieldsLeft: List[String]): Map[String, Any] = fieldsLeft match {
        case field::Nil => {
            // Last field
            data.groupBy(_(field).toString)
        }
        case field::remainder => {
            // First group, then ascend
            data.groupBy(_(field).toString).map(elem => elem._1 -> groupBuilder(elem._2, remainder))
        }
    }
}