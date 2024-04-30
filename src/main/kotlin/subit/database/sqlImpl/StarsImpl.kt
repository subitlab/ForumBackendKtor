package subit.database.sqlImpl

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import subit.dataClasses.PostId
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.Star
import subit.dataClasses.UserId
import subit.database.Stars

/**
 * 收藏数据库交互类
 */
class StarsImpl: DaoSqlImpl<StarsImpl.StarTable>(StarTable), Stars
{
    object StarTable: Table("stars")
    {
        val user = reference("user", UsersImpl.UserTable).index()
        val post = reference("post", PostsImpl.PostsTable, ReferenceOption.CASCADE, ReferenceOption.SET_NULL).nullable().default(null)
        val time = timestamp("time").defaultExpression(CurrentTimestamp()).index()
    }

    private fun deserialize(row: ResultRow) = Star(
        user = row[StarTable.user].value,
        post = row[StarTable.post]?.value,
        time = row[StarTable.time].toEpochMilli()
    )

    override suspend fun addStar(uid: UserId, pid: PostId): Unit = query()
    {
        insert {
            it[user] = uid
            it[post] = pid
        }
    }

    override suspend fun removeStar(uid: UserId, pid: PostId): Unit = query()
    {
        deleteWhere {
            (user eq uid) and (post eq pid)
        }
    }

    override suspend fun getStar(uid: UserId, pid: PostId): Boolean = query()
    {
        select {
            (user eq uid) and (post eq pid)
        }.count() > 0
    }

    override suspend fun getStarsCount(pid: PostId): Long = query()
    {
        StarTable.select { post eq pid }.count()
    }

    override suspend fun getStars(
        user: UserId?,
        post: PostId?,
        begin: Long,
        limit: Int,
    ): Slice<Star> = query()
    {
        select()
        {
            var op: Op<Boolean> = Op.TRUE
            if (user != null) op = op and (StarTable.user eq user)
            if (post != null) op = op and (StarTable.post eq post)
            op
        }.asSlice(begin, limit).map(::deserialize)
    }
}