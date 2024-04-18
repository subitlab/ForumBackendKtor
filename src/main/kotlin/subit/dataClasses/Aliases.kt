package subit.dataClasses

import org.jetbrains.exposed.sql.Table

typealias BlockId = Int
fun Table.blockId(name: String) = integer(name)
fun String.toBlockId() = toInt()
fun String.toBlockIdOrNull() = toIntOrNull()
typealias UserId = Int
fun Table.userId(name: String) = integer(name)
fun String.toUserId() = toInt()
fun String.toUserIdOrNull() = toIntOrNull()
typealias PostId = Long
fun Table.postId(name: String) = long(name)
fun String.toPostId() = toLong()
fun String.toPostIdOrNull() = toLongOrNull()

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