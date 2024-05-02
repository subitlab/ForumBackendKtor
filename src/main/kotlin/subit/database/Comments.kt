package subit.database

import subit.dataClasses.*

interface Comments
{
    /**
     * 创建评论
     * @param post 评论所属的帖子, 若为null要求[parent]不是null
     * @param parent 如果是二级评论, 则为父评论的id, 否则为null
     * @return 评论的id, null表示[post]或[parent]未找到
     */
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