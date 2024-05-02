package subit.database.sqlImpl

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.koin.core.component.KoinComponent
import subit.dataClasses.*
import subit.dataClasses.Notice.*
import subit.dataClasses.Notice.Type.*
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.Notices
import subit.utils.toUUIDOrNull
import java.util.*

class NoticesImpl: DaoSqlImpl<NoticesImpl.NoticesTable>(NoticesTable), Notices, KoinComponent
{
    object NoticesTable: IdTable<NoticeId>("notices")
    {
        override val id = noticeId("id").autoIncrement().entityId()
        val user = reference("user", UsersImpl.UserTable).index()
        val type = enumerationByName<Type>("type", 20).index()
        val content = text("content")
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private fun deserialize(row: ResultRow) = table.run()
    {
        when (row[type])
        {
            SYSTEM       -> SystemNotice(row[id].value, row[user].value, row[content])
            COMMENT      -> {
                val (post, count) = row[content].let { it.toUUIDOrNull() }
                                        ?.let { it.mostSignificantBits to it.leastSignificantBits } ?: (0L to 1L)
                CommentNotice(row[id].value, row[user].value, post, count)
            }
            LIKE         -> {
                val (post, count) = row[content].let { it.toUUIDOrNull() }
                                        ?.let { it.mostSignificantBits to it.leastSignificantBits } ?: (0L to 1L)
                LikeNotice(row[id].value, row[user].value, post, count)
            }
            STAR         -> {
                val (post, count) = row[content].let { it.toUUIDOrNull() }
                                        ?.let { it.mostSignificantBits to it.leastSignificantBits } ?: (0L to 1L)
                StarNotice(row[id].value, row[user].value, post, count)
            }
            PRIVATE_CHAT -> PrivateChatNotice(row[id].value, row[user].value, row[content].toLongOrNull() ?: 1)
            REPORT       -> ReportNotice(row[id].value, row[user].value, row[content].toLongOrNull() ?: 1)
        }
    }

    override suspend fun createNotice(notice: Notice): NoticeId = query()
    {
        val serialized = when (notice)
        {
            is PostNotice -> UUID(notice.post, notice.count).toString()
            is CountNotice -> notice.count.toString()
            is SystemNotice -> notice.content
        }
        insertAndGetId {
            it[user] = notice.user
            it[type] = notice.type
            it[content] = serialized
        }.value
    }

    override suspend fun getNotice(id: NoticeId): Notice? = query()
    {
        select { table.id eq id }.singleOrNull()?.let(::deserialize)
    }

    override suspend fun getNotices(user: UserId, begin: Long, count: Int): Slice<Notice> = query()
    {
        select { table.user eq user }.orderBy(table.id, SortOrder.DESC).asSlice(begin, count).map(::deserialize)
    }

    override suspend fun deleteNotice(id: NoticeId): Unit = query()
    {
        deleteWhere { table.id eq id }
    }

    override suspend fun deleteNotices(user: UserId): Unit = query()
    {
        deleteWhere { table.user eq user }
    }
}