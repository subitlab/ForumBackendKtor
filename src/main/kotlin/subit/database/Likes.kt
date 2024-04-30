package subit.database

import subit.dataClasses.PostId
import subit.dataClasses.UserId

/**
 * @author nullaqua
 */
interface Likes
{
    /**
     * 点赞/点踩
     * @param uid 用户ID
     * @param pid 帖子ID
     * @param like true为点赞, false为点踩
     */
    suspend fun like(uid: UserId, pid: PostId, like: Boolean)

    /**
     * 取消点赞/点踩
     * @param uid 用户ID
     */
    suspend fun unlike(uid: UserId, pid: PostId)

    /**
     * 获取用户对帖子的点赞状态
     * @param uid 用户ID
     * @param pid 帖子ID
     * @return true为点赞, false为点踩, null为未点赞/点踩
     */
    suspend fun getLike(uid: UserId, pid: PostId): Boolean?

    /**
     * 获取帖子的点赞数和点踩数
     * @param post 帖子ID
     * @return 点赞数和点踩数
     */
    suspend fun getLikes(post: PostId): Pair<Long,Long>
}