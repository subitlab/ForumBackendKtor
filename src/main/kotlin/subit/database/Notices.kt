package subit.database

import subit.dataClasses.Notice
import subit.dataClasses.NoticeId
import subit.dataClasses.Slice
import subit.dataClasses.UserId

interface Notices
{
    suspend fun createNotice(
        notice: Notice
    ): NoticeId
    suspend fun getNotice(id: NoticeId): Notice?
    suspend fun getNotices(user: UserId, begin: Long, count: Int): Slice<Notice>
    suspend fun deleteNotice(id: NoticeId)
    suspend fun deleteNotices(user: UserId)
}