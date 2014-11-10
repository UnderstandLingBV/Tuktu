package tuktu.social.processors

import tuktu.api._
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._

/*class TwitterParallelProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def processor(config: JsValue): Enumeratee[DataPacket, DataPacket] = Enumeratee.map(data => {
        // Get all the Twitter credentials
        val twitterCreds = 
    }
}*/