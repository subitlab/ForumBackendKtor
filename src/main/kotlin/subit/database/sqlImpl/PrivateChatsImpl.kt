package subit.database.sqlImpl

import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.bitwiseXor
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import subit.dataClasses.PrivateChat
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserId
import subit.database.PrivateChats

class PrivateChatsImpl: DaoSqlImpl<PrivateChatsImpl.PrivateChatsTable>(PrivateChatsTable), PrivateChats
{
    object PrivateChatsTable: Table("private_chats")
    {
        val from = reference("from", UsersImpl.UserTable).index()
        val to = reference("to", UsersImpl.UserTable).index()
        val time = timestamp("time").index().defaultExpression(CurrentTimestamp)
        val content = text("content")
    }

    private fun deserialize(row: ResultRow) = PrivateChat(
        from = row[PrivateChatsTable.from].value,
        to = row[PrivateChatsTable.to].value,
        time = row[PrivateChatsTable.time].toEpochMilliseconds(),
        content = row[PrivateChatsTable.content]
    )

    /**
     * 读取/设置未读消息数
     * @param from 发送消息者
     * @param to 接收消息者
     * @param block 读取未读消息数的回调, 传入当前未读消息数, 返回新的未读消息数
     * @return 未读消息数(若发生变化则为新的未读消息数)
     */
    private suspend fun unreadCount(from: UserId, to: UserId, block: ((Long)->Long)? = null): Long = query()
    {
        /**
         * 接手的程序员必读:
         *
         * 由于数据库无法直接存储未读消息数, 且新开table显得冗余, 所以这里采用了一个小技巧.
         * 当一个用户 user0 对于 user1 的消息有n条未读消息时, 会在数据库中插入一条记录:
         *
         * from = user0, to = user1, time = Instant.MIN, content = n.toString()
         * content字段存储未读消息数, time字段存储Instant.MIN, 用于区分未读消息数和已读消息
         *
         * 需要注意的是, 由于[PrivateChatsTable]没有设置任何约束, 所以需要注意维护, 避免出现有多个满足
         * from = user0, to = user1, time = Instant.MIN的数据
         *
         * 这里的实现是当未读消息数为0时删除记录, 当未读消息从0变为n时插入记录, 当未读消息数从n变为0时删除记录.
         * 这样可以保证数据库中只有一条记录, 且未读消息数为0时不占用额外空间.
         * 若有更好的实现方法, 可以自行修改.
         */
        val count = select(content).where {
            (PrivateChatsTable.from eq from) and (PrivateChatsTable.to eq to) and (time eq Instant.DISTANT_PAST)
        }.singleOrNull()?.get(content)?.toLongOrNull() ?: 0
        if (block != null)
        {
            val newCount = block(count)
            if (newCount != count)
            {
                if (newCount == 0L) deleteWhere {
                    (PrivateChatsTable.from eq from)
                        .and(PrivateChatsTable.to eq to)
                        .and(time eq Instant.DISTANT_PAST)
                }
                else if (count == 0L) insert {
                    it[PrivateChatsTable.from] = from
                    it[PrivateChatsTable.to] = to
                    it[time] = Instant.DISTANT_PAST
                    it[content] = newCount.toString()
                }
                else update({
                    (PrivateChatsTable.from eq from) and (PrivateChatsTable.to eq to) and (time eq Instant.DISTANT_PAST)
                })
                {
                    it[content] = newCount.toString()
                }
            }
            newCount
        }
        else count
    }

    override suspend fun addPrivateChat(from: UserId, to: UserId, content: String): Unit = query()
    {
        insert {
            it[PrivateChatsTable.from] = from
            it[PrivateChatsTable.to] = to
            it[PrivateChatsTable.content] = content
        }
        unreadCount(from, to) { it+1 }
    }

    private suspend fun getPrivateChats(
        user1: UserId,
        user2: UserId,
        before: Instant? = null,
        after: Instant? = null,
        begin: Long,
        count: Int
    ): Slice<PrivateChat> = query()
    {
        selectAll().where {
            val time = if (before != null) time lessEq before
            else if (after != null) time greaterEq after
            else Op.TRUE
            val x = (from eq user1) and (to eq user2)
            val y = (from eq user2) and (to eq user1)
            time and (x or y)
        }.apply {
            if (before != null) orderBy(time, SortOrder.DESC)
            else orderBy(time, SortOrder.ASC)
        }.asSlice(begin, count).map(::deserialize)
    }

    override suspend fun getPrivateChatsBefore(
        user1: UserId,
        user2: UserId,
        time: Instant,
        begin: Long,
        count: Int
    ): Slice<PrivateChat> = getPrivateChats(user1, user2, before = time, begin = begin, count = count)

    override suspend fun getPrivateChatsAfter(
        user1: UserId,
        user2: UserId,
        time: Instant,
        begin: Long,
        count: Int
    ): Slice<PrivateChat> = getPrivateChats(user1, user2, after = time, begin = begin, count = count)

    override suspend fun getChatUsers(uid: UserId, begin: Long, count: Int): Slice<UserId> = query()
    {
        /**
         * 由于在查询的时候需要包含双向的消息, 但是不清楚[PrivateChatsTable.from]还是[PrivateChatsTable.to]是当前用户,
         * 所以这里采用了一个小技巧, 将 from 和 to 异或, 因为异或满足交换律, 且from和to总有一个是当前用户,
         * 所以按照from xor to分组, 可以避免因为from和to交换导致的重复数据, 或数据遗漏
         */
        select(from, to) // 选择 from, to 两列, 因为只需要获得用户, 避免获取content, time以造成网络浪费
            .where { (from eq uid) or (to eq uid) } // 发送或接受消息者是要查询的用户
            .groupBy( from bitwiseXor to ) // 将 from 和 to 异或, 以保证 from 和 to 的组合是唯一的
            .orderBy(time.max(), SortOrder.DESC) // 按照时间最大值降序排序
            .withDistinct(true) // 去重
            .asSlice(begin, count) // 生成切片
            .map { it[from].value xor it[to].value xor uid } // 懒得判断from和to哪个是uid, 直接全部异或, 就是另一个用户
    }

    override suspend fun getUnreadCount(uid: UserId, other: UserId): Long = unreadCount(uid, other)
    override suspend fun getUnreadCount(uid: UserId): Long = query()
    {
        select(content).where { (from eq uid) and (time eq Instant.DISTANT_PAST) }
            .mapNotNull { it[content].toLongOrNull() }
            .sum()
    }

    override suspend fun setRead(uid: UserId, other: UserId)
    {
        unreadCount(uid, other) { 0 }
    }

    override suspend fun setReadAll(uid: UserId): Unit = query()
    {
        deleteWhere { (from eq uid) and (time eq Instant.DISTANT_PAST) }
    }
}