package subit.database.memoryImpl

import subit.dataClasses.Prohibit
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserId
import subit.database.Prohibits
import java.util.*

class ProhibitsImpl: Prohibits
{
    private val map = Collections.synchronizedMap(hashMapOf<UserId, Prohibit>())

    private fun clearProhibit()
    {
        val now = System.currentTimeMillis()
        map.entries.removeIf { it.value.time < now }
    }

    override suspend fun addProhibit(prohibit: Prohibit)
    {
        clearProhibit()
        map[prohibit.user] = prohibit
    }
    override suspend fun removeProhibit(uid: UserId)
    {
        clearProhibit()
        map.remove(uid)
    }
    override suspend fun isProhibited(uid: UserId): Boolean
    {
        clearProhibit()
        return map.containsKey(uid)
    }
    override suspend fun getProhibitList(begin: Long, count: Int): Slice<Prohibit>
    {
        clearProhibit()
        return map.values.sortedBy { it.time }.asSequence().asSlice(begin, count)
    }
}