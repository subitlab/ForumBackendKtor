@file:Suppress("unused")
package subit.dataClasses

import org.jetbrains.exposed.sql.Table

typealias BlockId = Int
fun Table.blockId(name: String) = integer(name)
fun String.toBlockId() = toInt()
fun String.toBlockIdOrNull() = toIntOrNull()
fun Number.toBlockId() = toInt()
typealias UserId = Int
fun Table.userId(name: String) = integer(name)
fun String.toUserId() = toInt()
fun String.toUserIdOrNull() = toIntOrNull()
fun Number.toUserId() = toInt()
typealias PostId = Long
fun Table.postId(name: String) = long(name)
fun String.toPostId() = toLong()
fun String.toPostIdOrNull() = toLongOrNull()
fun Number.toPostId() = toLong()
typealias CommentId = Long
fun Table.commentId(name: String) = long(name)
fun String.toCommentId() = toLong()
fun String.toCommentIdOrNull() = toLongOrNull()
fun Number.toCommentId() = toLong()
typealias ReportId = Long
fun Table.reportId(name: String) = long(name)
fun String.toReportId() = toLong()
fun String.toReportIdOrNull() = toLongOrNull()
fun Number.toReportId() = toLong()

typealias RawBlockUserId = ULong
@JvmInline
value class BlockUserId(val raw: RawBlockUserId)
{
    constructor(uid: Int, bid: Int): this((bid.toULong() shl 32) or uid.toULong())
    fun split() = (raw shr 32).toInt() to raw.toInt()
    val uid: UserId
        get() = raw.toInt()
    val bid: BlockId
        get() = (raw shr 32).toInt()
}
fun Table.blockUserId(name: String) = ulong(name)