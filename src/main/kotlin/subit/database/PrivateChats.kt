package subit.database

import subit.dataClasses.PrivateChat
import subit.dataClasses.Slice
import subit.dataClasses.UserId

interface PrivateChats
{
    suspend fun addPrivateChat(from: UserId, to: UserId, content: String)
    suspend fun getPrivateChats(from: UserId, to: UserId, begin: Long, count: Int): Slice<PrivateChat>
    suspend fun getChatUsers(uid: UserId, begin: Long, count: Int): Slice<UserId>
    suspend fun getUnreadCount(uid: UserId, other: UserId): Long
    suspend fun setRead(uid: UserId, other: UserId)
    suspend fun setReadAll(uid: UserId)
}