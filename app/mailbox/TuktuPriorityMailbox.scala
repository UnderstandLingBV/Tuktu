package mailbox

import akka.actor.ActorSystem
import akka.dispatch.BoundedPriorityMailbox
import com.typesafe.config.Config
import akka.dispatch.PriorityGenerator
import akka.actor.PoisonPill
import play.api.Play
import concurrent.duration.DurationInt
import tuktu.api.BackPressurePacket
import tuktu.api.InitPacket
import tuktu.api.StopPacket

class TuktuPriorityMailbox(settings: ActorSystem.Settings, config: Config) extends BoundedPriorityMailbox(
        PriorityGenerator {
            case BackPressurePacket => 0
            case PoisonPill => 5 // Least important
            case StopPacket => 4
            case InitPacket => 1
            case otherwise => 3
        },
        Play.current.configuration.getInt("tuktu.mailbox.capacity").getOrElse(10000),
        Play.current.configuration.getInt("tuktu.mailbox.push-timeout").getOrElse(100) milliseconds
)