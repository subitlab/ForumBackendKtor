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
        HOT
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
     * 获取帖子列表
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
//    suspend fun addLike(pid: PostId)
//    suspend fun addStar(pid: PostId)
}