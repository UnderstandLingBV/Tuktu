package tuktu.dfs.actors

import akka.actor.ActorLogging
import akka.actor.Actor

/**
 * Central point of communication for the DFS
 */
class MasterActor extends Actor with ActorLogging {
    def receive() = {
        case _ => {}
    }
}