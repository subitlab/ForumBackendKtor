package subit.database

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import subit.dataClasses.*

object CommentDatabase: DataAccessObject<CommentDatabase.Comments>(Comments)
{
    object Comments: IdTable<Long>("comments")
    {
        override val id = commentId("id").autoIncrement().entityId()
        val post = reference("post", PostDatabase.Posts).index()
        val parent = reference("parent", Comments).nullable().index()
        val author = reference("author", UserDatabase.Users).index()
        val content = text("content")
        val create = timestamp("create").defaultExpression(CurrentTimestamp()).index()
        val state = enumeration<State>("state").default(State.NORMAL)
        override val primaryKey = PrimaryKey(id)
    }

    private fun deserialize(row: ResultRow) = Comment(
        id = row[Comments.id].value,
        post = row[Comments.post].value,
        parent = row[Comments.parent]?.value,
        author = row[Comments.author].value,
        content = row[Comments.content],
        create = row[Comments.create].toEpochMilli(),
        state = row[Comments.state]
    )

    suspend fun createComment(
        post: PostId?,
        parent: CommentId?,
        author: UserId,
        content: String
    ): CommentId? = query()
    {
        parent?.let {
            val comment = getComment(it) ?: return@query null
            if (comment.post != post) return@query null
        }
        if (post == null) return@query null
        insertAndGetId {
            it[Comments.post] = post
            it[Comments.parent] = parent
            it[Comments.author] = author
            it[Comments.content] = content
        }.value
    }

    suspend fun getComment(id: CommentId): Comment? = query()
    {
        select {
            Comments.id eq id
        }.firstOrNull()?.let(::deserialize)
    }

    suspend fun setCommentState(id: CommentId, state: State) = query()
    {
        update({ Comments.id eq id })
        {
            it[Comments.state] = state
        }
    }

    suspend fun getComments(
        post: PostId? = null,
        parent: CommentId? = null,
    ): List<Comment>? = query()
    {
        if (post == null && parent == null) return@query null
        val post0 = post ?: parent?.let { getComment(it)?.post } ?: return@query null
        select { (Comments.post eq post0) and (Comments.parent eq parent) }.map(::deserialize)
    }
}