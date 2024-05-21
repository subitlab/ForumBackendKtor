package subit.database

import kotlinx.datetime.Instant
import subit.dataClasses.PrivateChat
import subit.dataClasses.Slice
import subit.dataClasses.UserId

interface PrivateChats
{
    suspend fun addPrivateChat(from: UserId, to: UserId, content: String)

    /**
     * 获取两人间的聊天记录, 返回的应是包含双向消息且比time早, 按照时间逆序排序分页的
     */
    suspend fun getPrivateChatsBefore(
        user1: UserId,
        user2: UserId,
        time: Instant,
        begin: Long,
        count: Int
    ): Slice<PrivateChat>

    /**
     * 获取两人间的聊天记录, 返回的应是包含双向消息且比time晚, 按照时间正序排序分页的
     */
    suspend fun getPrivateChatsAfter(
        user1: UserId,
        user2: UserId,
        time: Instant,
        begin: Long,
        count: Int
    ): Slice<PrivateChat>

    suspend fun getChatUsers(uid: UserId, begin: Long, count: Int): Slice<UserId>
    suspend fun getUnreadCount(uid: UserId, other: UserId): Long
    suspend fun getUnreadCount(uid: UserId): Long
    suspend fun setRead(uid: UserId, other: UserId)
    suspend fun setReadAll(uid: UserId)

    suspend fun getIsBlock(from: UserId, to: UserId): Boolean
    suspend fun setIsBlock(from: UserId, to: UserId, isBlock: Boolean)
}