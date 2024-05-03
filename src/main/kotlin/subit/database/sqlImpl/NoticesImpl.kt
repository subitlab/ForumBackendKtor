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

class NoticesImpl: DaoSqlImpl<NoticesImpl.NoticesTable>(NoticesTable), Notices, KoinComponent
{
    object NoticesTable: IdTable<NoticeId>("notices")
    {
        override val id = noticeId("id").autoIncrement().entityId()
        val user = reference("user", UsersImpl.UserTable).index()
        val type = enumerationByName<Type>("type", 20).index()
        val obj = long("object").nullable()
        val content = text("content")
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private fun deserialize(row: ResultRow) = table.run()
    {
        val id = row[id].value
        val obj = row[obj]
        // 如果是系统通知
        if (row[type] == SYSTEM) Notice.makeSystemNotice(id, row[user].value, row[content])
        // 否则一定是对象消息
        else Notice.makeObjectMessage(id, row[user].value, row[type], obj!!, row[content].toLong())
    }

    override suspend fun createNotice(notice: Notice): Unit = query()
    {
        // 如果是系统通知，直接插入一条新消息
        if (notice is SystemNotice) insert {
            it[user] = notice.user
            it[type] = notice.type
            it[obj] = null
            it[content] = notice.content
        }
        // 否则需要考虑同类型消息的合并
        else if (notice is ObjectNotice)
        {
            val result = select {
                (user eq notice.user) and (type eq notice.type) and (table.obj eq notice.obj)
            }.singleOrNull()

            if (result == null) insert {
                it[user] = notice.user
                it[type] = notice.type
                it[this.obj] = obj
                it[this.content] = content
            }
            else
            {
                val id = result[table.id].value
                val count = (result[table.content].toLongOrNull() ?: 1) + notice.count
                update({ table.id eq id }) { it[content] = count.toString() }
            }
        }
        else error("Unknown notice type: $notice")
    }

    override suspend fun getNotice(id: NoticeId): Notice? = query()
    {
        select { table.id eq id }.singleOrNull()?.let(::deserialize)
    }

    override suspend fun getNotices(user: UserId, type: Type?, begin: Long, count: Int): Slice<Notice> = query()
    {
        select {
            if (type == null) table.user eq user
            else (table.user eq user) and (table.type eq type)
        }.orderBy(table.id, SortOrder.DESC).asSlice(begin, count).map(::deserialize)
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