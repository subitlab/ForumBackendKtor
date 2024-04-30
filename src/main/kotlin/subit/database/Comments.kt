package subit.database

import subit.dataClasses.*

interface Comments
{
    suspend fun createComment(
        post: PostId?,
        parent: CommentId?,
        author: UserId,
        content: String
    ): CommentId?
    suspend fun getComment(id: CommentId): Comment?
    suspend fun setCommentState(id: CommentId, state: State)
    suspend fun getComments(
        post: PostId? = null,
        parent: CommentId? = null,
    ): List<Comment>?
}