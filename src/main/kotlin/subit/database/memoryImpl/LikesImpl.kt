package subit.database.memoryImpl

import subit.dataClasses.PostId
import subit.dataClasses.UserId
import subit.database.Likes
import java.util.Collections

class LikesImpl: Likes
{
    private val map = Collections.synchronizedMap(mutableMapOf<Pair<UserId,PostId>,Boolean>())

    override suspend fun like(uid: UserId, pid: PostId, like: Boolean)
    {
        map[uid to pid] = like
    }
    override suspend fun unlike(uid: UserId, pid: PostId)
    {
        map.remove(uid to pid)
    }
    override suspend fun getLike(uid: UserId, pid: PostId): Boolean?
    {
        return map[uid to pid]
    }
    override suspend fun getLikes(post: PostId): Pair<Long, Long>
    {
        val likes = map.entries.filter { it.key.second == post }
        return likes.count { it.value }.toLong() to likes.count { !it.value }.toLong()
    }
}