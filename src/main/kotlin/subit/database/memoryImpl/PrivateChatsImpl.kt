package subit.database.memoryImpl

import kotlinx.datetime.Instant
import subit.dataClasses.PrivateChat
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserId
import subit.database.PrivateChats
import java.util.Collections

class PrivateChatsImpl: PrivateChats
{
    private val privateChats = Collections.synchronizedMap(hashMapOf<Long, MutableList<PrivateChat>>())
    private val unread = Collections.synchronizedMap(hashMapOf<Long, Long>())
    private fun makeList() = Collections.synchronizedList(mutableListOf<PrivateChat>())
    private infix fun UserId.link(other: UserId): Long = (this.toLong() shl 32) or other.toLong()
    override suspend fun addPrivateChat(from: UserId, to: UserId, content: String)
    {
        val list = privateChats.getOrPut(from link to) { makeList() }
        list.add(PrivateChat(from, to, System.currentTimeMillis(), content))
    }

    private fun getPrivateChats(
        user1: UserId,
        user2: UserId,
        before: Long? = null,
        after: Long? = null,
        begin: Long,
        count: Int
    ): Slice<PrivateChat>
    {
        val list1 = privateChats[user1 link user2] ?: emptyList()
        val list2 = privateChats[user2 link user1] ?: emptyList()
        val list = (list1+list2).let { list ->
            if (before != null) list.filter { it.time <= before }
            else if (after != null) list.filter { it.time >= after }
            else list
        }.let { list ->
            if (before != null) list.sortedByDescending { it.time }
            else if (after != null) list.sortedBy { it.time }
            else list
        }.asSlice(begin, count)
        return list
    }

    override suspend fun getPrivateChatsBefore(
        user1: UserId,
        user2: UserId,
        time: Instant,
        begin: Long,
        count: Int
    ): Slice<PrivateChat> =
        getPrivateChats(user1, user2, before = time.toEpochMilliseconds(), begin = begin, count = count)

    override suspend fun getPrivateChatsAfter(
        user1: UserId,
        user2: UserId,
        time: Instant,
        begin: Long,
        count: Int
    ): Slice<PrivateChat> =
        getPrivateChats(user1, user2, after = time.toEpochMilliseconds(), begin = begin, count = count)

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