package mailbox

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Terminated
import akka.actor.DeadLetter
import akka.actor.PoisonPill
import tuktu.api.BackPressurePacket

class DeadLetterWatcher(target: ActorRef) extends Actor with ActorLogging {
    private val targetPath = target.path

    override def preStart() {
        context.watch(target)
    }

    def receive: Actor.Receive = {
        case d: DeadLetter =>
            if (d.recipient.path.equals(targetPath)) {
                log.debug(s"Timed out message: ${d.message.toString}")
                target ! new BackPressurePacket()
            }

        case Terminated(`target`) =>
            self ! PoisonPill
    }
}