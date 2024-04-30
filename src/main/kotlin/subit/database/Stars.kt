package subit.database

import subit.dataClasses.PostId
import subit.dataClasses.Slice
import subit.dataClasses.Star
import subit.dataClasses.UserId

interface Stars
{
    suspend fun addStar(uid: UserId, pid: PostId)
    suspend fun removeStar(uid: UserId, pid: PostId)
    suspend fun getStar(uid: UserId, pid: PostId): Boolean
    suspend fun getStarsCount(pid: PostId): Long
    suspend fun getStars(
        user: UserId? = null,
        post: PostId? = null,
        begin: Long = 1,
        limit: Int = Int.MAX_VALUE,
    ): Slice<Star>
}