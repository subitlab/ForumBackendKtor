package subit.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import subit.dataClasses.*
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice

/**
 * 收藏数据库交互类
 */
object StarDatabase: DataAccessObject<StarDatabase.Stars>(Stars)
{
    object Stars: Table("stars")
    {
        val user = reference("user", UserDatabase.Users).index()
        val post = reference("post", PostDatabase.Posts, ReferenceOption.CASCADE, ReferenceOption.SET_NULL).nullable().default(null)
        val time = timestamp("time").defaultExpression(CurrentTimestamp()).index()
    }

    private fun deserialize(row: ResultRow) = Star(
        user = row[Stars.user].value,
        post = row[Stars.post]?.value,
        time = row[Stars.time].toEpochMilli()
    )

    suspend fun addStar(uid: UserId, pid: PostId) = query()
    {
        insert {
            it[user] = uid
            it[post] = pid
        }
    }

    suspend fun removeStar(uid: UserId, pid: PostId) = query()
    {
        deleteWhere {
            (Stars.user eq uid) and (Stars.post eq pid)
        }
    }

    suspend fun getStar(uid: UserId, pid: PostId): Boolean = query()
    {
        select {
            (Stars.user eq uid) and (Stars.post eq pid)
        }.count() > 0
    }

    suspend fun getStarsCount(pid: PostId): Long = query()
    {
        Stars.select { Stars.post eq pid }.count()
    }

    suspend fun getStars(
        user: UserId? = null,
        post: PostId? = null,
        begin: Long = 1,
        limit: Int = Int.MAX_VALUE,
    ): Slice<Star> = query()
    {
        selectBatched()
        {
            var op: Op<Boolean> = Op.TRUE
            if (user != null) op = op and (Stars.user eq user)
            if (post != null) op = op and (Stars.post eq post)
            op
        }.flattenAsIterable().asSlice(begin, limit).map(::deserialize)
    }
}