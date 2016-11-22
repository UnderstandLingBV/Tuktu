package tuktu.api

import akka.actor.ActorRef

/**
 * Requests
 */
case class DBObject(
        key: String,
        value: Map[String, Any]
)
case class StoreRequest(
        elements: List[DBObject],
        needReply: Boolean
)
case class ReplicateRequest(
        elements: List[DBObject],
        needReply: Boolean
)
case class ReadRequest(
        key: String,
        originalSender: Option[ActorRef]
)
case class DeleteRequest(
        key: String,
        needReply: Boolean
)
case class DeleteActionRequest(
        key: String,
        needReply: Boolean
)
case class PersistRequest()
case class OverviewRequest(
        offset: Int
)
case class OverviewReply(
        bucketCounts: Map[String, Int]
)
case class ContentRequest(
        key: String,
        offset: Int
)
case class ContentReply(
        data: List[Map[String, Any]]
)
case class ReadResponse(
        value: List[Map[String, Any]]
)