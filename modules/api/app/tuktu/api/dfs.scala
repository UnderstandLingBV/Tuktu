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
case class DFSOpenRequest(
        filename: String,
        encoding: String
)
case class DFSOpenReadRequest(
        filename: String
)
case class DFSLocalOpenRequest(
        filename: String
)
case class DFSWriteRequest(
        filename: String,
        content: String
)
case class DFSCloseRequest(
        filename: String
)
case class DFSLocalCloseRequest(
        filename: String
)
case class DFSCloseReadRequest(
        filename: String
)
case class DFSListRequest(
        filename: String
)
case class DFSOpenFileListRequest()

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
case class DFSResponse(
        files: Map[String, DFSElement],
        isDirectory: Boolean
)
case class DFSOpenFileListResponse(
        localFiles: List[String],
        remoteFiles: List[String],
        readFiles: List[String]
)

case class DFSElement(isDirectory: Boolean)