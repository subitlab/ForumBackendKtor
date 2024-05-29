package subit.dataClasses

import kotlinx.serialization.Serializable

/**
 * 评论信息
 * @property id 评论ID
 * @property post 所属帖子ID
 * @property parent 父评论ID
 * @property author 作者ID
 * @property content 内容
 * @property create 创建时间
 * @property state 状态
 */
@Serializable
data class Comment(
    val id: CommentId,
    val post: PostId,
    val parent: CommentId?,
    val author: UserId,
    val content: String,
    val create: Long,
    val state: State
)
{
    companion object
    {
        val example = Comment(
            CommentId(1),
            PostId(1),
            null,
            UserId(1),
            "评论内容",
            System.currentTimeMillis(),
            State.NORMAL
        )
    }
}