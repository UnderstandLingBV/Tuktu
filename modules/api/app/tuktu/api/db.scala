package tuktu.api

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
        key: String
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

/**
 * Responses
 */
case class ReadResponse(
        value: List[Map[String, Any]]
)