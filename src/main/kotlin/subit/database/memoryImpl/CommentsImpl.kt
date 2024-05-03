package subit.database.memoryImpl

import subit.dataClasses.*
import subit.database.Comments
import java.util.*

class CommentsImpl: Comments
{
    private val map = Collections.synchronizedMap(mutableMapOf<CommentId, Comment>())
    override suspend fun createComment(post: PostId?, parent: CommentId?, author: UserId, content: String): CommentId?
    {
        if (post == null && parent == null) return null
        val post1 = post ?: parent?.let { getComment(it)?.post } ?: return null
        val id = (map.size+1).toCommentId()
        map[id] = Comment(
            id = id,
            post = post1,
            parent = parent,
            author = author,
            content = content,
            create = System.currentTimeMillis(),
            state = State.NORMAL
        )
        return id
    }

    override suspend fun getComment(id: CommentId): Comment? = map[id]
    override suspend fun setCommentState(id: CommentId, state: State)
    {
        val c = map[id] ?: return
        map[id] = c.copy(state = state)
    }

    override suspend fun getComments(post: PostId?, parent: CommentId?): List<Comment>?
    {
        if (post == null && parent == null) return null
        val post0 = post ?: parent?.let { getComment(it)?.post } ?: return null
        return map.values.filter { it.post == post0 && it.parent == parent }
    }

    fun getLastComment(post: PostId): Date
    {
        return map.values.filter { it.post == post }.maxOfOrNull { it.create }?.let { Date(it) } ?: Date(0)
    }

    fun getCommentCount(post: PostId): Int
    {
        return map.values.count { it.post == post }
    }
}