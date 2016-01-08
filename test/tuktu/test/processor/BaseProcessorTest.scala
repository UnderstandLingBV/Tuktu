package tuktu.test.processor

import tuktu.api.DataPacket
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import tuktu.test.testUtil
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import org.scalatest.Assertions._

/**
 * Base class to test a processor
 */
class BaseProcessorTest {
    def apply(processor: BaseProcessor, config: JsObject, input: List[DataPacket],
              expectedOutcomes: List[DataPacket]*)(implicit timeout: Int = 5) {
        // Initialize
        processor.initialize(config)

        // Build the required enumerator and iteratee and run the enumeratee
        val iter: Iteratee[DataPacket, List[DataPacket]] = Iteratee.fold(List[DataPacket]())(_ ++ List(_))
        val obtainedList = Await.result(Enumerator.enumerate(input).run(processor.processor() &>> iter), timeout seconds)

        // Compare/inspect the output
        val res = obtainedList.zip(expectedOutcomes).forall(packets => {
            val obtained = packets._1
            val expected = packets._2

            // Inspect the data inside the packets
            (obtained.data.isEmpty && expected.exists(_ isEmpty)) ||
                expected.exists(exp => obtained.data.zip(exp.data).forall(data => testUtil.inspectMaps(data._1, data._2)))
        })

        assertResult(true, "Obtained output is:\r\n" + obtainedList + "\r\nExpected:\r\n" + expectedOutcomes.mkString("\r\n")) {
            (obtainedList.isEmpty && expectedOutcomes.exists(_ isEmpty)) || (!obtainedList.isEmpty && !expectedOutcomes.exists(_ isEmpty) && res)
        }
    }
}