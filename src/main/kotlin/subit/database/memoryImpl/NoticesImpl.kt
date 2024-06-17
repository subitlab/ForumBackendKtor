package subit.database.memoryImpl

import subit.dataClasses.Notice
import subit.dataClasses.NoticeId
import subit.dataClasses.NoticeId.Companion.toNoticeId
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserId
import subit.database.Notices
import java.util.*

class NoticesImpl: Notices
{
    private val notices = Collections.synchronizedMap(hashMapOf<NoticeId, Notice>())

    override suspend fun createNotice(notice: Notice)
    {
        val id = (notices.size + 1).toNoticeId()
        notices[id] = when (notice)
        {
            is Notice.SystemNotice -> Notice.makeSystemNotice(id, notice.user, notice.content)
            is Notice.ObjectNotice -> Notice.makeObjectMessage(id, notice.user, notice.type, notice.obj, notice.count)
        }
    }

    override suspend fun getNotice(id: NoticeId): Notice? = notices[id]

    override suspend fun getNotices(user: UserId, type: Notice.Type?, begin: Long, count: Int): Slice<Notice> =
        notices.values.filter { it.user == user && (type == null || it.type == type) }
            .asSequence()
            .asSlice(begin, count)

    override suspend fun deleteNotice(id: NoticeId)
    {
        notices.remove(id)
    }

    override suspend fun deleteNotices(user: UserId)
    {
        notices.entries.removeIf { it.value.user == user }
    }
}