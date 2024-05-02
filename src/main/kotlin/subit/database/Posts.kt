package subit.database

import kotlinx.serialization.Serializable
import subit.dataClasses.*

interface Posts
{
    @Serializable
    enum class PostListSort
    {
        NEW,
        OLD,
        MORE_VIEW,
        MORE_LIKE,
        MORE_STAR,
        MORE_COMMENT,
        LAST_COMMENT
    }

    suspend fun createPost(
        title: String,
        content: String,
        author: UserId,
        anonymous: Boolean,
        block: BlockId,
        top: Boolean = false
    ): PostId

    suspend fun editPost(pid: PostId, title: String, content: String)
    suspend fun setPostState(pid: PostId, state: State)
    suspend fun getPost(pid: PostId): PostInfo?

    /**
     * 获取用户发布的帖子
     * @param loginUser 当前操作用户, null表示未登录, 返回的帖子应是该用户可见的.
     */
    suspend fun getUserPosts(
        loginUser: UserFull? = null,
        author: UserId,
        begin: Long = 1,
        limit: Int = Int.MAX_VALUE,
    ): Slice<PostInfo>

    suspend fun getBlockPosts(
        block: BlockId,
        type: PostListSort,
        begin: Long,
        count: Int
    ): Slice<PostInfo>

    suspend fun getBlockTopPosts(block: BlockId, begin: Long, count: Int): Slice<PostInfo>
    suspend fun getPosts(list: Slice<PostId?>): Slice<PostInfo?>
    suspend fun searchPosts(loginUser: UserId?, key: String, begin: Long, count: Int): Slice<PostInfo>
    suspend fun addView(pid: PostId)

    /**
     * 获取首页推荐, 应按照时间/浏览量/点赞等参数随机, 即越新/点赞越高/浏览量越高...随机到的几率越大.
     * @param count 推荐数量
     */
    suspend fun getRecommendPosts(count: Int): Slice<PostId>
}