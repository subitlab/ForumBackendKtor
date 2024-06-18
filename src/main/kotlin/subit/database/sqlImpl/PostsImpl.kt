package subit.database.sqlImpl

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.KotlinInstantColumnType
import org.jetbrains.exposed.sql.kotlin.datetime.Minute
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import subit.dataClasses.*
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.*
import subit.database.Posts.PostListSort.*
import subit.database.sqlImpl.PostsImpl.PostsTable.create
import subit.database.sqlImpl.PostsImpl.PostsTable.view

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
        override val id = postId("id").autoIncrement().entityId()
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

    @Suppress("RemoveRedundantQualifierName")
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

    override suspend fun editPost(pid: PostId, title: String?, content: String?, top: Boolean?): Unit = query()
    {
        update({ id eq pid })
        { postInfo ->
            title?.let { postInfo[PostsTable.title] = title }
            content?.let { postInfo[PostsTable.content] = content }
            top?.let { postInfo[PostsTable.top] = top }
            postInfo[lastModified] = CurrentTimestamp
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
        loginUser: UserId?,
        author: UserId,
        begin: Long,
        limit: Int,
    ): Slice<PostId> = query()
    {
        // 构建查询，联结 PostsTable, BlocksTable 和 PermissionsTable
        val permissionTable = (permissions as PermissionsImpl).table
        val blockTable = (blocks as BlocksImpl).table

        if (loginUser == null)
        {
            return@query PostsTable.join(blockTable, JoinType.INNER, block, blockTable.id)
                .select(id)
                .where { (PostsTable.author eq author) and (state eq State.NORMAL) and (blockTable.reading lessEq PermissionLevel.NORMAL) }
                .orderBy(create, SortOrder.DESC)
                .asSlice(begin, limit)
                .map { it[id].value }
        }

        PostsTable.join(blockTable, JoinType.INNER, block, blockTable.id)
            .join(permissionTable, JoinType.LEFT, block, permissionTable.block) { permissionTable.user eq loginUser }
            .select(id)
            .where { PostsTable.author eq author }
            .andWhere { state eq State.NORMAL }
            .groupBy(id, create, blockTable.id, blockTable.reading)
            .having { (permissionTable.permission.max() greaterEq blockTable.reading) or (blockTable.reading lessEq PermissionLevel.NORMAL) }
            .orderBy(create, SortOrder.DESC)
            .asSlice(begin, limit)
            .map { it[id].value }
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

    override suspend fun getBlockTopPosts(block: BlockId, begin: Long, count: Int): Slice<PostId> = query()
    {
        PostsTable.select(id)
            .where { PostsTable.block eq block }
            .andWhere { top eq true }
            .andWhere { state eq State.NORMAL }
            .asSlice(begin, count)
            .map { it[id].value }
    }

    override suspend fun searchPosts(
        loginUser: UserId?,
        key: String,
        begin: Long,
        count: Int
    ): Slice<PostId> = query()
    {
        val permissionTable = (permissions as PermissionsImpl).table
        val blockTable = (blocks as BlocksImpl).table
        val likesTable = (likes as LikesImpl).table
        val starsTable = (stars as StarsImpl).table
        val commentsTable = (comments as CommentsImpl).table
        val additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? =
            if (loginUser != null) ({ permissionTable.user eq loginUser })
            else null
        PostsTable.join(blockTable, JoinType.INNER, block, blockTable.id)
            .join(permissionTable, JoinType.LEFT, block, permissionTable.block, additionalConstraint)
            .join(likesTable, JoinType.LEFT, id, likesTable.post)
            .join(starsTable, JoinType.LEFT, id, starsTable.post)
            .join(commentsTable, JoinType.LEFT, id, commentsTable.post)
            .select(id)
            .where { (title like "%$key%") or (content like "%$key%") }
            .andWhere { state eq State.NORMAL }
            .groupBy(id, create, blockTable.id, blockTable.reading)
            .having { (permissionTable.permission.max() greaterEq blockTable.reading) or (blockTable.reading lessEq PermissionLevel.NORMAL) }
            .orderBy(hotScoreOrder, SortOrder.DESC)
            .asSlice(begin, count)
            .map { it[id].value }
    }

    override suspend fun addView(pid: PostId): Unit = query()
    {
        PostsTable.update({ id eq pid }) { it[view] = view + 1 }
    }

    private val hotScoreOrder by lazy {
        val x =
            (view + LikesImpl.LikesTable.like.count() * 3 + StarsImpl.StarsTable.post.count() * 5 + CommentsImpl.CommentsTable.id.count() * 2 + 1)
        val now = CustomFunction("NOW", KotlinInstantColumnType())

        @Suppress("UNCHECKED_CAST")
        val minute = (Minute(now - create) as Function<Double> + 1.0) / 1024.0
        val order = x / CustomFunction("POW", LongColumnType(), minute, doubleParam(1.8))
        order
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
         * 按照 (浏览量+点赞数*3+收藏数*5+评论数*2)/(发帖到现在的时间(单位: 时间)的1.8次方)
         *
         * 计算发帖到现在的时间需要使用函数TIMESTAMPDIFF(MINUTE, create, NOW())
         */

        table.join(blocksTable, JoinType.INNER, block, blocksTable.id)
            .join(likesTable, JoinType.LEFT, id, likesTable.post)
            .join(starsTable, JoinType.LEFT, id, starsTable.post)
            .join(commentsTable, JoinType.LEFT, id, commentsTable.post)
            .select(id)
            .where { (blocksTable.reading lessEq PermissionLevel.NORMAL) and (state eq State.NORMAL) }
            .groupBy(id)
            .orderBy(hotScoreOrder, SortOrder.DESC)
            .asSlice(0, count)
            .map { it[id].value }
    }
}