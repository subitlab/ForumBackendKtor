package subit.database

import subit.dataClasses.Prohibit
import subit.dataClasses.Slice
import subit.dataClasses.UserId

interface Prohibits
{
    suspend fun addProhibit(prohibit: Prohibit)
    suspend fun removeProhibit(uid: UserId)

    /**
     * 检查用户是否被禁止, true代表被封禁
     */
    suspend fun isProhibited(uid: UserId): Boolean
    suspend fun getProhibitList(begin: Long, count: Int): Slice<Prohibit>
}