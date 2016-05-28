package tuktu.web.processors.analytics

import play.api.libs.iteratee.Enumeratee
import tuktu.api.DataPacket
import tuktu.api.BaseJsProcessor
import play.api.libs.json.JsObject
import scala.concurrent.Future
import tuktu.api.WebJsSrcObject
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api.WebJsCodeObject

/**
 * Fingerprints a user's browser/mobile/OS details
 */
class FingerprintProcessor(resultName: String) extends BaseJsProcessor(resultName) {
    override def initialize(config: JsObject) {
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        for (datum <- data) yield addJsElements(datum, List(
                new WebJsSrcObject(
                        // Inclusion of fingerprint plugin
                        "//cdnjs.cloudflare.com/ajax/libs/fingerprintjs2/1.3.0/fingerprint2.min.js"
                ),
                new WebJsCodeObject(
                        "new Fingerprint2().get(function(r, c){ tuktuvars." + resultName + "={'hash':r,'fp':c}; }"
                )
        ))
    })
}