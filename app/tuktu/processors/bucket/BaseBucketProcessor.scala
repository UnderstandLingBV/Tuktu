package tuktu.processors.bucket

import tuktu.api._
import play.api.libs.iteratee.Enumeratee
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

abstract class BaseBucketProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => Future {
        new DataPacket(doProcess(data.data))
    })
    
    def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = ???
}