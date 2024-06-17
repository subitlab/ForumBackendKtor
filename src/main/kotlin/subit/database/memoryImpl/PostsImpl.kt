package subit.database.memoryImpl

import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import subit.dataClasses.*
import subit.dataClasses.PostId.Companion.toPostId
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.*
import java.util.*
import kotlin.math.pow

class PostsImpl: Posts, KoinComponent
{
    private val map = Collections.synchronizedMap(hashMapOf<PostId, Pair<PostInfo, Boolean>>())
    private val blocks: Blocks by inject()
    private val permissions: Permissions by inject()
    private val likes: Likes by inject()
    private val comments: Comments by inject()
    private val stars: Stars by inject()
    override suspend fun createPost(
        title: String,
        content: String,
        author: UserId,
        anonymous: Boolean,
        block: BlockId,
        top: Boolean
    ): PostId
    {
        val id = (map.size+1).toPostId()
        map[id] = PostInfo(
            id = id,
            title = title,
            content = content,
            author = author,
            anonymous = anonymous,
            block = block,
            state = State.NORMAL,
            create = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            view = 0
        ) to top
        return id
    }

    override suspend fun editPost(pid: PostId, title: String?, content: String?, top: Boolean?)
    {
        val post = map[pid] ?: return
        map[pid] =
            (post.first.copy(title = title ?: post.first.title, content = content ?: post.first.content, lastModified = System.currentTimeMillis())) to (top ?: post.second)
    }

    override suspend fun setPostState(pid: PostId, state: State)
    {
        val post = map[pid] ?: return
        map[pid] = post.first.copy(state = state) to post.second
    }

    override suspend fun getPost(pid: PostId): PostInfo? = map[pid]?.first

    override suspend fun getUserPosts(loginUser: UserId?, author: UserId, begin: Long, limit: Int): Slice<PostId> =
        map.values.filter { it.first.author == author }
            .filter {
                val blockFull = blocks.getBlock(it.first.block) ?: return@filter false
                val permission = loginUser?.let { permissions.getPermission(blockFull.id, loginUser) }
                                 ?: PermissionLevel.NORMAL
                permission >= blockFull.reading && (it.first.state == State.NORMAL)
            }
            .map { it.first }
            .asSequence()
            .asSlice(begin, limit)
            .map { it.id }

    override suspend fun getBlockPosts(
        block: BlockId,
        type: Posts.PostListSort,
        begin: Long,
        count: Int
    ): Slice<PostId> = map.values
        .filter { it.first.block == block }
        .map { it.first }
        .sortedBy {
            when (type)
            {
                Posts.PostListSort.NEW          -> -it.create
                Posts.PostListSort.OLD          -> it.create
                Posts.PostListSort.MORE_VIEW    -> -it.view
                Posts.PostListSort.MORE_LIKE    -> runBlocking { -stars.getStarsCount(it.id) }
                Posts.PostListSort.MORE_STAR    -> runBlocking { -likes.getLikes(it.id).first }
                Posts.PostListSort.MORE_COMMENT -> (comments as CommentsImpl).getCommentCount(it.id).toLong()
                Posts.PostListSort.LAST_COMMENT -> -(comments as CommentsImpl).getLastComment(it.id).time
            }
        }
        .asSequence()
        .asSlice(begin, count)
        .map { it.id }

    override suspend fun getBlockTopPosts(block: BlockId, begin: Long, count: Int): Slice<PostId> = map.values
        .filter { it.first.block == block && it.second }
        .map { it.first }
        .asSequence()
        .asSlice(begin, count)
        .map { it.id }

    override suspend fun searchPosts(loginUser: UserId?, key: String, begin: Long, count: Int): Slice<PostId> = map.values
        .filter { it.first.title.contains(key) || it.first.content.contains(key) }
        .filter {
            val blockFull = blocks.getBlock(it.first.block) ?: return@filter false
            val permission = loginUser?.let { permissions.getPermission(blockFull.id, loginUser) }
                             ?: PermissionLevel.NORMAL
            permission >= blockFull.reading
        }
        .asSequence()
        .asSlice(begin, count)
        .map { it.first.id }

    override suspend fun addView(pid: PostId)
    {
        val post = map[pid] ?: return
        map[pid] = post.first.copy(view = post.first.view+1) to post.second
    }

    private fun getHotScore(pid: PostId): Double
    {
        val post = map[pid]?.first ?: return 0.0
        val likesCount = runBlocking { likes.getLikes(pid).first }
        val starsCount = runBlocking { stars.getStarsCount(pid) }
        val commentsCount = (comments as CommentsImpl).getCommentCount(pid)
        val time = (System.currentTimeMillis()-post.create).toDouble()/1000/*s*//60/*m*//60/*h*/
        return (post.view+likesCount*3+starsCount*5+commentsCount*2)/time.pow(1.8)
    }

    override suspend fun getRecommendPosts(count: Int): Slice<PostId> = map.values
        .filter { it.first.state == State.NORMAL }
        .sortedByDescending { getHotScore(it.first.id) }
        .asSequence()
        .asSlice(1, count)
        .map { it.first.id }
}
