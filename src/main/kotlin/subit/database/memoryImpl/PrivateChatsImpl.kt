package subit.database.memoryImpl

import kotlinx.datetime.Instant
import subit.dataClasses.PrivateChat
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserId
import subit.dataClasses.UserId.Companion.toUserId
import subit.database.PrivateChats
import java.util.*

class PrivateChatsImpl: PrivateChats
{
    private val privateChats = Collections.synchronizedMap(hashMapOf<Long, MutableList<PrivateChat>>())
    private val unread = Collections.synchronizedMap(hashMapOf<Long, Long>())
    private fun makeList() = Collections.synchronizedList(mutableListOf<PrivateChat>())
    private infix fun UserId.link(other: UserId): Long = (this.value.toLong() shl 32) or other.value.toLong()
    override suspend fun addPrivateChat(from: UserId, to: UserId, content: String)
    {
        val list = privateChats.getOrPut(from link to) { makeList() }
        list.add(PrivateChat(from, to, System.currentTimeMillis(), content))
        unread[from link to] = (unread[from link to] ?: 0) + 1
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
        return (list1+list2).let { list ->
            if (before != null) list.filter { it.time <= before }
            else if (after != null) list.filter { it.time >= after }
            else list
        }.let { list ->
            if (before != null) list.sortedByDescending { it.time }
            else if (after != null) list.sortedBy { it.time }
            else list
        }.asSlice(begin, count)
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
        val list = privateChats.keys.asSequence()
            .filter { it ushr 32 == uid.value.toLong() || it and 0xffffffff == uid.value.toLong() }
            .groupBy { (it ushr 32) xor (it and 0xffffffff) xor uid.value.toLong() }
            .map { it.key to it.value.max() }
            .sortedByDescending { it.second }
            .map { it.first.toUserId() }.toList()
        return list.asSlice(begin, count)
    }

    override suspend fun getUnreadCount(uid: UserId, other: UserId): Long = unread[other link uid] ?: 0
    override suspend fun getUnreadCount(uid: UserId): Long = unread.values.filter { it.toInt() == uid.value }.sum()
    override suspend fun setRead(uid: UserId, other: UserId)
    {
        unread[other link uid] = 0
    }

    override suspend fun setReadAll(uid: UserId)
    {
        unread.keys.removeIf { it.toInt() == uid.value }
    }
}