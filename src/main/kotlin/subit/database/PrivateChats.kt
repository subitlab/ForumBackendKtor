package subit.database

import subit.dataClasses.PrivateChat
import subit.dataClasses.Slice
import subit.dataClasses.UserId

interface PrivateChats
{
    suspend fun addPrivateChat(from: UserId, to: UserId, content: String)

    /**
     * 获取两人间的聊天记录, 返回的应是包含双向消息且按照时间顺序排序分页的
     */
    suspend fun getPrivateChats(user1: UserId, user2: UserId, begin: Long, count: Int): Slice<PrivateChat>
    suspend fun getChatUsers(uid: UserId, begin: Long, count: Int): Slice<UserId>
    suspend fun getUnreadCount(uid: UserId, other: UserId): Long
    suspend fun getUnreadCount(uid: UserId): Long
    suspend fun setRead(uid: UserId, other: UserId)
    suspend fun setReadAll(uid: UserId)
}