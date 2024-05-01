package subit.database.sqlImpl

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import subit.dataClasses.*
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.Blocks
import subit.database.Permissions
import subit.database.Posts

/**
 * 帖子数据库交互类
 */
class PostsImpl: DaoSqlImpl<PostsImpl.PostsTable>(PostsTable), Posts, KoinComponent
{
    private val blocks: Blocks by inject()
    private val permissions: Permissions by inject()

    object PostsTable: IdTable<PostId>("posts")
    {
        override val id: Column<EntityID<PostId>> = postId("id").autoIncrement().entityId()
        val title = varchar("title", 100).index()
        val content = text("content")
        val author = reference("author", UsersImpl.UserTable).index()
        val anonymous = bool("anonymous").default(false)
        val block = reference("block", BlocksImpl.BlocksTable, ReferenceOption.CASCADE, ReferenceOption.CASCADE).index()
        val create = timestamp("create").defaultExpression(CurrentTimestamp()).index()
        val lastModified = timestamp("last_modified").defaultExpression(CurrentTimestamp()).index()
        val view = long("view").default(0L)
        val state = enumeration("state", State::class).default(State.NORMAL)
        val top = bool("top").default(false)
        override val primaryKey = PrimaryKey(id)
    }

    private fun deserializePost(row: ResultRow): PostInfo = PostInfo(
        id = row[PostsTable.id].value,
        title = row[PostsTable.title],
        content = row[PostsTable.content],
        author = row[PostsTable.author].value,
        anonymous = row[PostsTable.anonymous],
        create = row[PostsTable.create].toEpochMilli(),
        lastModified = row[PostsTable.lastModified].toEpochMilli(),
        view = row[PostsTable.view],
        block = row[PostsTable.block].value,
        state = row[PostsTable.state]
    )

    override suspend fun createPost(
        title: String,
        content: String,
        author: UserId,
        anonymous: Boolean,
        block: BlockId,
        top: Boolean
    ): PostId = query()
    {
        PostsTable.insertAndGetId {
            it[PostsTable.title] = title
            it[PostsTable.content] = content
            it[PostsTable.author] = author
            it[PostsTable.anonymous] = anonymous
            it[PostsTable.block] = block
            it[PostsTable.top] = top
        }.value
    }

    override suspend fun editPost(pid: PostId, title: String, content: String): Unit = query()
    {
        update({ id eq pid })
        {
            it[PostsTable.title] = title
            it[PostsTable.content] = content
            it[lastModified] = CurrentTimestamp()
        }
    }

    override suspend fun setPostState(pid: PostId, state: State): Unit = query()
    {
        update({ id eq pid }) { it[PostsTable.state] = state }
    }

    override suspend fun getPost(pid: PostId): PostInfo? = query()
    {
        select { id eq pid }.firstOrNull()?.let(::deserializePost)
    }

    /**
     * 获取帖子列表
     */
    override suspend fun getUserPosts(
        loginUser: UserFull?,
        author: UserId,
        begin: Long,
        limit: Int,
    ): Slice<PostInfo> = query()
    {
        select { PostsTable.author eq author }.asSlice(begin, limit) { row ->
            val blockFull = blocks.getBlock(row[block].value) ?: return@asSlice false
            val permission = loginUser?.let { permissions.getPermission(loginUser.id, blockFull.id) }
                             ?: PermissionLevel.NORMAL
            permission >= blockFull.reading && (row[state] == State.NORMAL || loginUser.hasGlobalAdmin())
        }.map(::deserializePost)
    }

    override suspend fun getBlockPosts(
        block: BlockId,
        type: Posts.PostListSort,
        begin: Long,
        count: Int
    ): Slice<PostInfo> = query()
    {
        val order = when (type)
        {
            Posts.PostListSort.NEW       -> create to SortOrder.DESC
            Posts.PostListSort.OLD       -> create to SortOrder.ASC
            Posts.PostListSort.MORE_VIEW -> view to SortOrder.DESC
        }
        PostsTable.select { (PostsTable.block eq block) and (state eq State.NORMAL) }
            .orderBy(order.first, order.second)
            .asSlice(begin, count)
            .map(::deserializePost)
    }

    override suspend fun getBlockTopPosts(block: BlockId, begin: Long, count: Int): Slice<PostInfo> = query()
    {
        PostsTable.select { (PostsTable.block eq block) and (top eq true) and (state eq State.NORMAL) }
            .asSlice(begin, count)
            .map(::deserializePost)
    }

    override suspend fun getPosts(list: Slice<PostId?>): Slice<PostInfo?> = query()
    {
        list.map {
            if (it != null) select { id eq it }.firstOrNull()
            else null
        }.map { it?.let(::deserializePost) }
    }

    override suspend fun searchPosts(
        loginUser: UserId?,
        key: String,
        begin: Long,
        count: Int
    ): Slice<PostInfo> = query()
    {
        PostsTable.select { ((title like "%$key%") or (content like "%$key%")) and (state eq State.NORMAL) }
            .asSlice(begin, count) {
                val blockFull = blocks.getBlock(it[block].value) ?: return@asSlice false
                val permission = loginUser?.let { permissions.getPermission(loginUser, blockFull.id) }
                                 ?: PermissionLevel.NORMAL
                permission >= blockFull.reading
            }.map(::deserializePost)
    }

    override suspend fun addView(pid: PostId): Unit = query()
    {
        PostsTable.update({ id eq pid }) { it[view] = view+1 }
    }

    override suspend fun getRecommendPosts(count: Int): Slice<PostId>
    {
        TODO("Not yet implemented")
    }
}