@file:Suppress("unused")

package subit.dataClasses

import kotlinx.serialization.Serializable

/// 各种类型别名, 和相关类型转换等方法. 在需要用到这些类型的地方尽量使用别名, 以便于需要修改时全局修改 ///

interface Id<T: Number>
{
    val value: T
    companion object
    {
        /**
         * 未知的ID
         */
        fun <T: Number> unknown(id: T): Id<T> = object : Id<T>
        {
            override val value: T = id
            override fun toString(): String = id.toString()
        }
    }
}

@JvmInline
@Serializable
value class BlockId(override val value: Int): Comparable<BlockId>, Id<Int>
{
    override fun compareTo(other: BlockId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toBlockId() = BlockId(toInt())
        fun String.toBlockIdOrNull() = toIntOrNull()?.let(::BlockId)
        fun Number.toBlockId() = BlockId(toInt())
    }
}

@JvmInline
@Serializable
value class UserId(override val value: Int): Comparable<UserId>, Id<Int>
{
    override fun compareTo(other: UserId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toUserId() = UserId(toInt())
        fun String.toUserIdOrNull() = toIntOrNull()?.let(::UserId)
        fun Number.toUserId() = UserId(toInt())
    }
}

@JvmInline
@Serializable
value class PostId(override val value: Long): Comparable<PostId>, Id<Long>
{
    override fun compareTo(other: PostId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toPostId() = PostId(toLong())
        fun String.toPostIdOrNull() = toLongOrNull()?.let(::PostId)
        fun Number.toPostId() = PostId(toLong())
    }
}

@JvmInline
@Serializable
value class CommentId(override val value: Long): Comparable<CommentId>, Id<Long>
{
    override fun compareTo(other: CommentId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toCommentId() = CommentId(toLong())
        fun String.toCommentIdOrNull() = toLongOrNull()?.let(::CommentId)
        fun Number.toCommentId() = CommentId(toLong())
    }
}

@JvmInline
@Serializable
value class ReportId(override val value: Long): Comparable<ReportId>, Id<Long>
{
    override fun compareTo(other: ReportId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toReportId() = ReportId(toLong())
        fun String.toReportIdOrNull() = toLongOrNull()?.let(::ReportId)
        fun Number.toReportId() = ReportId(toLong())
    }
}

@JvmInline
@Serializable
value class NoticeId(override val value: Long): Comparable<NoticeId>, Id<Long>
{
    override fun compareTo(other: NoticeId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toNoticeId() = NoticeId(toLong())
        fun String.toNoticeIdOrNull() = toLongOrNull()?.let(::NoticeId)
        fun Number.toNoticeId() = NoticeId(toLong())
    }
}

@JvmInline
@Serializable
value class BlockUserId private constructor(override val value: Long): Comparable<BlockUserId>, Id<Long>
{
    constructor(uid: UserId, bid: BlockId): this((bid.value.toLong() shl 32) or uid.value.toLong())
    override fun compareTo(other: BlockUserId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toBlockUserId() = BlockUserId(toLong())
        fun String.toBlockUserIdOrNull() = toLongOrNull()?.let(::BlockUserId)
        fun Number.toBlockUserId() = BlockUserId(toLong())
        fun byRawValue(value: Long) = BlockUserId(value)
    }

    val user get() = UserId(value.toInt())
    val block get() = BlockId((value shr 32).toInt())
}