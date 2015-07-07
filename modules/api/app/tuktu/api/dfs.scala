package tuktu.api

import java.io.File

// Requests
case class DFSReadRequest(
        filename: String
)
case class DFSCreateRequest(
        filename: String,
        isDirectory: Boolean
)
case class DFSDeleteRequest(
        filename: String,
        isDirectory: Boolean
)

// Replies
case class DFSReadReply(
        files: Option[File]
)
case class DFSCreateReply(
        file: Option[File]
)
case class DFSDeleteReply(
        success: Boolean
)