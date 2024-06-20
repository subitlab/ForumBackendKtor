@file:Suppress("unused")

package subit.dataClasses

import kotlinx.serialization.Serializable

/// 各种类型别名, 和相关类型转换等方法. 在需要用到这些类型的地方尽量使用别名, 以便于需要修改时全局修改 ///

interface Id<ID, T>: Comparable<ID> where T: Number, T: Comparable<T>, ID: Id<ID, T>
{
    val value: T
    override fun compareTo(other: ID): Int = value.compareTo(other.value)

    companion object
    {
        /**
         * 未知的ID
         */
        fun <T> unknown(id: T): Id<*, T> where T: Number, T: Comparable<T> = object: Id<Nothing, T>
        {
            override val value: T = id
            override fun toString(): String = id.toString()
        }
    }
}

typealias RawBlockId = Int
@JvmInline
@Serializable
value class BlockId(override val value: RawBlockId): Id<BlockId, RawBlockId>
{
    override fun toString(): String = value.toString()

    companion object
    {
        fun String.toBlockId() = BlockId(toInt())
        fun String.toBlockIdOrNull() = toIntOrNull()?.let(::BlockId)
        fun Number.toBlockId() = BlockId(toInt())
    }
}

typealias RawUserId = Int
@JvmInline
@Serializable
value class UserId(override val value: RawUserId): Id<UserId, RawUserId>
{
    override fun toString(): String = value.toString()

    companion object
    {
        fun String.toUserId() = UserId(toInt())
        fun String.toUserIdOrNull() = toIntOrNull()?.let(::UserId)
        fun Number.toUserId() = UserId(toInt())
    }
}

typealias RawPostId = Long
@JvmInline
@Serializable
value class PostId(override val value: RawPostId): Id<PostId, RawPostId>
{
    override fun toString(): String = value.toString()

    companion object
    {
        fun String.toPostId() = PostId(toLong())
        fun String.toPostIdOrNull() = toLongOrNull()?.let(::PostId)
        fun Number.toPostId() = PostId(toLong())
    }
}

typealias RawCommentId = Long
@JvmInline
@Serializable
value class CommentId(override val value: RawCommentId): Id<CommentId, RawCommentId>
{
    override fun toString(): String = value.toString()

    companion object
    {
        fun String.toCommentId() = CommentId(toLong())
        fun String.toCommentIdOrNull() = toLongOrNull()?.let(::CommentId)
        fun Number.toCommentId() = CommentId(toLong())
    }
}

typealias RawReportId = Long
@JvmInline
@Serializable
value class ReportId(override val value: RawReportId): Id<ReportId, RawReportId>
{
    override fun toString(): String = value.toString()

    companion object
    {
        fun String.toReportId() = ReportId(toLong())
        fun String.toReportIdOrNull() = toLongOrNull()?.let(::ReportId)
        fun Number.toReportId() = ReportId(toLong())
    }
}

typealias RawNoticeId = Long
@JvmInline
@Serializable
value class NoticeId(override val value: RawNoticeId): Id<NoticeId, RawNoticeId>
{
    override fun toString(): String = value.toString()

    companion object
    {
        fun String.toNoticeId() = NoticeId(toLong())
        fun String.toNoticeIdOrNull() = toLongOrNull()?.let(::NoticeId)
        fun Number.toNoticeId() = NoticeId(toLong())
    }
}