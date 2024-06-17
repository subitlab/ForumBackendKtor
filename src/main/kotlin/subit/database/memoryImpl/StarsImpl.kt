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
    private val set = Collections.synchronizedSet(hashSetOf<Star>())
    override suspend fun addStar(uid: UserId, pid: PostId)
    {
        set.add(Star(uid, pid, System.currentTimeMillis()))
    }

    override suspend fun removeStar(uid: UserId, pid: PostId)
    {
        set.removeIf { it.user == uid && it.post == pid }
    }

    override suspend fun getStar(uid: UserId, pid: PostId): Boolean =
        set.count { it.user == uid && it.post == pid } > 0

    override suspend fun getStarsCount(pid: PostId): Long =
        set.count { it.post == pid }.toLong()

    override suspend fun getStars(user: UserId?, post: PostId?, begin: Long, limit: Int): Slice<Star> =
        set.filter { (user == null || it.user == user) && (post == null || it.post == post) }
            .sortedByDescending(Star::time).asSequence().asSlice(begin, limit)
}