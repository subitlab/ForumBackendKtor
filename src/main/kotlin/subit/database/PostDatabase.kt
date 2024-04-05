package subit.database

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

@Serializable
data class PostFull(
    val id: Long,
    val title: String,
    val content: String,
    val author: Long,
    val anonymous: Boolean,
    val create: String,
    val lastModified: String,
    val view: UInt,
    val like: UInt,
    val dislike: UInt,
    val star: UInt,
    val block: UInt,
    val state: PostDatabase.PostState,
)

/**
 * 帖子数据库交互类
 */
object PostDatabase: DataAccessObject<PostDatabase.Posts>(Posts)
{
    object Posts: IdTable<Long>("posts")
    {
        /**
         * 帖子ID
         */
        override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()

        /**
         * 帖子标题
         */
        val title = varchar("title", 100).index()

        /**
         * 帖子内容
         */
        val content = text("content")

        /**
         * 帖子作者
         */
        val author = reference("author", UserDatabase.Users).index()

        /**
         * 此贴作者匿名
         */
        val anonymous = bool("anonymous").default(false)

        /**
         * 帖子创建时间
         */
        val create = datetime("create").defaultExpression(CurrentDateTime).index()

        /**
         * 帖子最后修改时间
         */
        val lastModified = datetime("lastModified").defaultExpression(CurrentDateTime).index()

        /**
         * 帖子浏览量
         */
        val view = uinteger("view").default(0U).index()

        /**
         * 帖子点赞数
         */
        val like = uinteger("like").default(0U).index()

        /**
         * 帖子点踩数
         */
        val dislike = uinteger("dislike").default(0U)

        /**
         * 帖子收藏数
         */
        val star = uinteger("star").default(0U)

        /**
         * 帖子所属板块
         */
        val block = reference("block", BlockDatabase.Blocks, ReferenceOption.CASCADE, ReferenceOption.CASCADE).index()

        /**
         * 帖子当前状态
         */
        val state = enumeration("state", PostState::class).default(PostState.PENDING)
        override val primaryKey = PrimaryKey(id)
    }

    /**
     * 帖子当前状态,
     */
    enum class PostState
    {
        /**
         * 待审核
         */
        PENDING,

        /**
         * 正常
         */
        NORMAL,

        /**
         * 被隐藏(审核不通过)
         */
        HIDDEN,

        /**
         * 被删除
         */
        DELETED;
    }

    suspend fun setPostState(pid: Long, state: PostState) = query()
    {
        update({ Posts.id eq pid }) { it[Posts.state] = state }
    }

    suspend fun getPostFull(pid: Long): PostFull? = query()
    {
        select() { Posts.id eq pid }.firstOrNull()?.let { deserialize<PostFull>(it) }
    }
}