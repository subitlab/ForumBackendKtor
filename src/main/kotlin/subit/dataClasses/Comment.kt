package subit.dataClasses

import kotlinx.serialization.Serializable

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