package subit.database.sqlImpl

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.koin.core.component.KoinComponent
import subit.dataClasses.*
import subit.dataClasses.Slice.Companion.singleOrNull
import subit.database.Comments

class CommentsImpl: DaoSqlImpl<CommentsImpl.CommentsTable>(CommentsTable), Comments, KoinComponent
{
    object CommentsTable: IdTable<CommentId>("comments")
    {
        override val id = commentId("id").autoIncrement().entityId()
        val post = reference("post", PostsImpl.PostsTable).index()
        val parent = reference("parent", CommentsTable).nullable().index()
        val author = reference("author", UsersImpl.UserTable).index()
        val content = text("content")
        val create = timestamp("create").defaultExpression(CurrentTimestamp).index()
        val state = enumerationByName<State>("state", 20).default(State.NORMAL)
        override val primaryKey = PrimaryKey(id)
    }

    private fun deserialize(row: ResultRow) = Comment(
        id = row[CommentsTable.id].value,
        post = row[CommentsTable.post].value,
        parent = row[CommentsTable.parent]?.value,
        author = row[CommentsTable.author].value,
        content = row[CommentsTable.content],
        create = row[CommentsTable.create].toEpochMilliseconds(),
        state = row[CommentsTable.state]
    )

    override suspend fun createComment(
        post: PostId?,
        parent: CommentId?,
        author: UserId,
        content: String
    ): CommentId? = query()
    {
        if (post == null && parent == null) return@query null
        val post1 = post ?: parent?.let { getComment(it)?.post } ?: return@query null
        insertAndGetId {
            it[this.post] = post1
            it[this.parent] = parent
            it[this.author] = author
            it[this.content] = content
        }.value
    }

    override suspend fun getComment(id: CommentId): Comment? = query()
    {
        selectAll().where { CommentsTable.id eq id }.singleOrNull()?.let(::deserialize)
    }

    override suspend fun setCommentState(id: CommentId, state: State): Unit = query()
    {
        update({ CommentsTable.id eq id })
        {
            it[CommentsTable.state] = state
        }
    }

    override suspend fun getComments(
        post: PostId?,
        parent: CommentId?,
    ): List<Comment>? = query()
    {
        if (post == null && parent == null) return@query null
        val post0 = post ?: parent?.let { getComment(it)?.post } ?: return@query null
        selectAll().where { (CommentsTable.post eq post0) and (CommentsTable.parent eq parent) }.map(::deserialize)
    }
}