package subit.database.sqlImpl

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.SqlExpressionBuilder.wrap
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinInstantColumnType
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import subit.dataClasses.*
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.*
import subit.database.Posts.PostListSort.*

/**
 * 帖子数据库交互类
 */
class PostsImpl: DaoSqlImpl<PostsImpl.PostsTable>(PostsTable), Posts, KoinComponent
{
    private val blocks: Blocks by inject()
    private val likes: Likes by inject()
    private val stars: Stars by inject()
    private val comments: Comments by inject()
    private val permissions: Permissions by inject()

    object PostsTable: IdTable<PostId>("posts")
    {
        override val id: Column<EntityID<PostId>> = postId("id").autoIncrement().entityId()
        val title = varchar("title", 100).index()
        val content = text("content")
        val author = reference("author", UsersImpl.UserTable).index()
        val anonymous = bool("anonymous").default(false)
        val block = reference("block", BlocksImpl.BlocksTable, ReferenceOption.CASCADE, ReferenceOption.CASCADE).index()
        val create = timestamp("create").defaultExpression(CurrentTimestamp).index()
        val lastModified = timestamp("last_modified").defaultExpression(CurrentTimestamp).index()
        val view = long("view").default(0L)
        val state = enumerationByName<State>("state", 20).default(State.NORMAL)
        val top = bool("top").default(false)
        override val primaryKey = PrimaryKey(id)
    }

    private fun deserializePost(row: ResultRow): PostInfo = PostInfo(
        id = row[PostsTable.id].value,
        title = row[PostsTable.title],
        content = row[PostsTable.content],
        author = row[PostsTable.author].value,
        anonymous = row[PostsTable.anonymous],
        create = row[PostsTable.create].toEpochMilliseconds(),
        lastModified = row[PostsTable.lastModified].toEpochMilliseconds(),
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
            it[lastModified] = CurrentTimestamp
        }
    }

    override suspend fun setPostState(pid: PostId, state: State): Unit = query()
    {
        update({ id eq pid }) { it[PostsTable.state] = state }
    }

    override suspend fun getPost(pid: PostId): PostInfo? = query()
    {
        selectAll().where { id eq pid }.firstOrNull()?.let(::deserializePost)
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
        selectAll().where { PostsTable.author eq author }.asSlice(begin, limit) { row ->
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
    ): Slice<PostId> = query()
    {
        val op = (PostsTable.block eq block) and (state eq State.NORMAL)
        val r: Query = when (type)
        {
            NEW          -> select(id).where(op).orderBy(create, SortOrder.DESC)
            OLD          -> select(id).where(op).orderBy(create, SortOrder.ASC)
            MORE_VIEW    -> select(id).where(op).orderBy(view, SortOrder.DESC)
            MORE_LIKE    ->
            {
                val likesTable = (likes as LikesImpl).table
                PostsTable.join(likesTable, JoinType.LEFT, id, LikesImpl.LikesTable.post)
                    .select(id)
                    .where(op)
                    .groupBy(id)
                    .orderBy(likesTable.like.sum(), SortOrder.DESC)
            }

            MORE_STAR    ->
            {
                val starsTable = (stars as StarsImpl).table
                PostsTable.join(starsTable, JoinType.LEFT, id, StarsImpl.StarsTable.post)
                    .select(id)
                    .where(op)
                    .groupBy(id)
                    .orderBy(starsTable.post.count(), SortOrder.DESC)
            }

            MORE_COMMENT ->
            {
                val commentsTable = (comments as CommentsImpl).table
                PostsTable.join(commentsTable, JoinType.LEFT, id, CommentsImpl.CommentsTable.post)
                    .select(id)
                    .where(op)
                    .groupBy(id)
                    .orderBy(commentsTable.id.count(), SortOrder.DESC)
            }

            LAST_COMMENT ->
            {
                val commentsTable = (comments as CommentsImpl).table
                PostsTable.join(commentsTable, JoinType.LEFT, id, CommentsImpl.CommentsTable.post)
                    .select(id)
                    .where(op)
                    .groupBy(id)
                    .orderBy(commentsTable.create.max(), SortOrder.DESC)
            }
        }

        r.asSlice(begin, count).map { it[id].value }
    }

    override suspend fun getBlockTopPosts(block: BlockId, begin: Long, count: Int): Slice<PostInfo> = query()
    {
        PostsTable.selectAll().where { (PostsTable.block eq block) and (top eq true) and (state eq State.NORMAL) }
            .asSlice(begin, count)
            .map(::deserializePost)
    }

    override suspend fun getPosts(list: Slice<PostId?>): Slice<PostInfo?> = query()
    {
        list.map {
            if (it != null) selectAll().where { id eq it }.firstOrNull()
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
        PostsTable.selectAll().where { ((title like "%$key%") or (content like "%$key%")) and (state eq State.NORMAL) }
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

    override suspend fun getRecommendPosts(count: Int): Slice<PostId> = query()
    {
        val blocksTable = (blocks as BlocksImpl).table
        val likesTable = (likes as LikesImpl).table
        val starsTable = (stars as StarsImpl).table
        val commentsTable = (comments as CommentsImpl).table

        /**
         * 选择所属板块的reading权限小于等于NORMAL的帖子
         *
         * 按照 浏览量+点赞数*3+收藏数*5+评论数*2 加权随机
         */
        val minute = object: Expression<Nothing>()
        {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("MINUTE") }
        }
        val now = CustomFunction("NOW", KotlinInstantColumnType())
        val time = CustomFunction("TIMESTAMPDIFF", DoubleColumnType(), minute, create, now)+1.0
        val x = (view+likesTable.like.count()*3+starsTable.post.count()*5+commentsTable.id.count()*2)
        val order = x/CustomFunction("POW", LongColumnType(), time, doubleParam(1.8))
        table.join(blocksTable, JoinType.INNER, block, blocksTable.id)
            .join(likesTable, JoinType.LEFT, id, likesTable.post)
            .join(starsTable, JoinType.LEFT, id, starsTable.post)
            .join(commentsTable, JoinType.LEFT, id, commentsTable.post)
            .select(id)
            .where { (blocksTable.reading lessEq PermissionLevel.NORMAL) and (state eq State.NORMAL) }
            .groupBy(id)
            .orderBy(order, SortOrder.DESC)
            .asSlice(1, count)
            .map { it[id].value }
    }
}