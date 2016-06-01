package tuktu.api

/**
 * Requests
 */
case class DBObject(
        key: List[String],
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
        key: List[Any]
)
case class DeleteRequest(
        key: List[Any],
        needReply: Boolean
)
case class DeleteActionRequest(
        key: List[Any],
        needReply: Boolean
)
case class PersistRequest()
case class OverviewRequest(
        offset: Int
)
case class OverviewReply(
        bucketCounts: Map[List[Any], Int]
)

/**
 * Responses
 */
case class ReadResponse(
        value: List[Map[String, Any]]
)