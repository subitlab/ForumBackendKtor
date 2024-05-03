package subit.database.memoryImpl

import subit.dataClasses.PostId
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.Star
import subit.dataClasses.UserId
import subit.database.Stars
import java.util.*

class StarsImpl: Stars
{
    private val set = Collections.synchronizedSet(mutableSetOf<Star>())

    override suspend fun addStar(uid: UserId, pid: PostId)
    {
        set.add(Star(uid, pid, System.currentTimeMillis()))
    }
    override suspend fun removeStar(uid: UserId, pid: PostId)
    {
        set.removeIf { it.user == uid && it.post == pid }
    }
    override suspend fun getStar(uid: UserId, pid: PostId): Boolean
    {
        return set.count { it.user == uid && it.post == pid } > 0
    }
    override suspend fun getStarsCount(pid: PostId): Long
    {
        return set.count { it.post == pid }.toLong()
    }
    override suspend fun getStars(user: UserId?, post: PostId?, begin: Long, limit: Int): Slice<Star>
    {
        val list = set.filter { (user == null || it.user == user) && (post == null || it.post == post) }
            .sortedBy { -it.time }
        return list.asSlice(begin, limit)
    }
}