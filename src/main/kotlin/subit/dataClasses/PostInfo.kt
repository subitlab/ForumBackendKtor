package subit.dataClasses

import kotlinx.serialization.Serializable
import subit.database.LikesDatabase
import subit.database.StarDatabase

/**
 * 帖子信息
 * @property id 帖子ID
 * @property title 帖子标题
 * @property content 帖子内容
 * @property author 帖子作者
 * @property anonymous 此贴作者匿名
 * @property create 帖子创建时间
 * @property lastModified 帖子最后修改时间
 * @property view 帖子浏览量
 * @property block 帖子所属板块
 * @property state 帖子当前状态
 */
@Serializable
data class PostInfo(
    val id: PostId,
    val title: String,
    val content: String,
    val author: UserId,
    val anonymous: Boolean,
    val create: Long,
    val lastModified: Long,
    val view: Long,
    val block: Int,
    val state: State,
)
{
    suspend fun toPostFull(): PostFull
    {
        val (like,dislike) = LikesDatabase.getLikes(id)
        val star = StarDatabase.getStarsCount(id)
        return PostFull(id,title,content,author,anonymous,create,lastModified,view,block,state,like,dislike,star)
    }
}

@Serializable
data class PostFull(
    val id: PostId,
    val title: String,
    val content: String,
    val author: UserId?,
    val anonymous: Boolean,
    val create: Long,
    val lastModified: Long,
    val view: Long,
    val block: Int,
    val state: State,
    val like: Long,
    val dislike: Long,
    val star: Long,
)

/**
 * 帖子当前状态,
 */
enum class State
{
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