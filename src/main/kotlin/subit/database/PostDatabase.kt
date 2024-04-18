package subit.database

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import subit.dataClasses.*
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.PermissionDatabase.canRead

/**
 * 帖子数据库交互类
 */
object PostDatabase: DataAccessObject<PostDatabase.Posts>(Posts)
{
    object Posts: IdTable<PostId>("posts")
    {
        override val id: Column<EntityID<PostId>> = postId("id").autoIncrement().entityId()
        val title = varchar("title", 100).index()
        val content = text("content")
        val author = reference("author", UserDatabase.Users).index()
        val anonymous = bool("anonymous").default(false)
        val block = reference("block", BlockDatabase.Blocks, ReferenceOption.CASCADE, ReferenceOption.CASCADE).index()
        val create = timestamp("create").defaultExpression(CurrentTimestamp()).index()
        val lastModified = timestamp("last_modified").defaultExpression(CurrentTimestamp()).index()
        val view = long("view").default(0L)
        val state = enumeration("state", PostState::class).default(PostState.NORMAL)
        override val primaryKey = PrimaryKey(id)
    }

    private fun deserializePost(row: ResultRow): PostInfo = PostInfo(
        id = row[Posts.id].value,
        title = row[Posts.title],
        content = row[Posts.content],
        author = row[Posts.author].value,
        anonymous = row[Posts.anonymous],
        create = row[Posts.create].toEpochMilli(),
        lastModified = row[Posts.lastModified].toEpochMilli(),
        view = row[Posts.view],
        block = row[Posts.block].value,
        state = row[Posts.state]
    )

    suspend fun createPost(
        title: String,
        content: String,
        author: UserId,
        anonymous: Boolean,
        block: BlockId
    ): PostId = query()
    {
        Posts.insertAndGetId {
            it[Posts.title] = title
            it[Posts.content] = content
            it[Posts.author] = author
            it[Posts.anonymous] = anonymous
            it[Posts.block] = block
        }.value
    }

    suspend fun editPost(pid: PostId, title: String, content: String) = query()
    {
        update({ Posts.id eq pid })
        {
            it[Posts.title] = title
            it[Posts.content] = content
            it[Posts.lastModified] = CurrentTimestamp()
        }
    }

    suspend fun setPostState(pid: PostId, state: PostState) = query()
    {
        update({ Posts.id eq pid }) { it[Posts.state] = state }
    }

    suspend fun getPost(pid: PostId): PostInfo? = query()
    {
        select { Posts.id eq pid }.firstOrNull()?.let(::deserializePost)
    }

    /**
     * 获取帖子列表
     */
    suspend fun getPosts(
        loginUser: UserFull? = null,
        block: Int? = null,
        author: UserId? = null,
        begin: Long = 1,
        limit: Int = Int.MAX_VALUE,
    ): Slice<PostInfo> = query()
    {
        if (block != null && !loginUser.canRead(block)) return@query Slice.empty()
        selectBatched()
        {
            var op: Op<Boolean> = Op.TRUE
            if (block != null) op = op and (Posts.block eq block) // block不为空则匹配block
            if (author != null) op = op and (Posts.author eq author) // author不为空则匹配author
            if (loginUser == null || loginUser.permission < PermissionLevel.ADMIN) // 若用户不是管理员
                op = op and (Posts.state eq PostState.NORMAL)
            op
        }.flattenAsIterable().asSlice(begin, limit) {
            loginUser.canRead(it[Posts.block].value) // 过滤掉用户无权查看的帖子
        }.map(::deserializePost)
    }

    suspend fun getPosts(list: Slice<PostId?>): Slice<PostInfo?> = query()
    {
        list.map {
            if (it != null) select { Posts.id eq it }.firstOrNull()
            else null
        }.map { it?.let(::deserializePost) }
    }
}