package monitor

import akka.actor._
import akka.util.Timeout
import scala.concurrent.duration._
import tuktu.api._

object DataMonitor extends Actor with ActorLogging {
    implicit val timeout = Timeout(10 seconds)

    def receive() = {
        case packet: MonitorPacket => {}
    }
}