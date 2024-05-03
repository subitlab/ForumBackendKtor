package subit.database.memoryImpl

import subit.dataClasses.PrivateChat
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserId
import subit.database.PrivateChats
import java.util.Collections

class PrivateChatsImpl: PrivateChats
{
    private val privateChats = Collections.synchronizedMap(mutableMapOf<Long, MutableList<PrivateChat>>())
    private val unread = Collections.synchronizedMap(mutableMapOf<Long, Long>())

    private fun makeList() = Collections.synchronizedList(mutableListOf<PrivateChat>())
    private infix fun UserId.link(other: UserId): Long = (this.toLong() shl 32) or other.toLong()

    override suspend fun addPrivateChat(from: UserId, to: UserId, content: String)
    {
        val list = privateChats.getOrPut(from link to) { makeList() }
        list.add(PrivateChat(from, to, System.currentTimeMillis(), content))
    }
    override suspend fun getPrivateChats(user1: UserId, user2: UserId, begin: Long, count: Int): Slice<PrivateChat>
    {
        val list1 = privateChats[user1 link user2] ?: emptyList()
        val list2 = privateChats[user2 link user1] ?: emptyList()
        val list = (list1 + list2).sortedBy { -it.time }
        return list.asSlice(begin, count)
    }
    override suspend fun getChatUsers(uid: UserId, begin: Long, count: Int): Slice<UserId>
    {
        val list = privateChats.keys.filter { it ushr 32 == uid.toLong() || it and 0xffffffff == uid.toLong() }
            .map { it.toInt() }
        return list.asSlice(begin, count)
    }
    override suspend fun getUnreadCount(uid: UserId, other: UserId): Long = unread[uid link other] ?: 0
    override suspend fun getUnreadCount(uid: UserId): Long = unread.values.filter { it ushr 32 == uid.toLong() }.sum()
    override suspend fun setRead(uid: UserId, other: UserId)
    {
        unread.remove(uid link other)
    }
    override suspend fun setReadAll(uid: UserId)
    {
        unread.keys.removeIf { it ushr 32 == uid.toLong() }
    }
}