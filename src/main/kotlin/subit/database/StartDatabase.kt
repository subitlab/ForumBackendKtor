package subit.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * 收藏数据库交互类
 */
object StarDatabase: DataAccessObject<StarDatabase.Stars>(Stars)
{
    object Stars: Table("stars")
    {
        /**
         * 用户
         */
        val user = reference("user", UserDatabase.Users).index()

        /**
         * 收藏的帖子,在帖子删除时不会删除,在帖子修改时同步
         */
        val post = reference("post", PostDatabase.Posts, ReferenceOption.CASCADE, ReferenceOption.SET_NULL).nullable().default(null)

        /**
         * 收藏的时间
         */
        val time = datetime("time").defaultExpression(CurrentDateTime).index()
    }

    suspend fun addStar(uid: Long, pid: Long) = query()
    {
        insert {
            it[user] = uid
            it[post] = pid
        }
    }

    suspend fun removeStar(uid: Long, pid: Long) = query()
    {
        deleteWhere {
            (Stars.user eq uid) and (Stars.post eq pid)
        }
    }

    suspend fun getStar(uid: Long, pid: Long): Boolean = query()
    {
        select {
            (Stars.user eq uid) and (Stars.post eq pid)
        }.count() > 0
    }

    suspend fun getUserStarList(uid: Long): List<Long> = query()
    {
        select {
            Stars.user eq uid
        }.map { it[Stars.post]?.value?:0L }
    }

    suspend fun getPostStarList(pid: Long): List<Long> = query()
    {
        select {
            Stars.post eq pid
        }.map { it[Stars.user].value }
    }

    suspend fun getPostStarCount(pid: Long): Long = query()
    {
        select {
            Stars.post eq pid
        }.count()
    }

    suspend fun getUserStarCount(uid: Long): Long = query()
    {
        select {
            Stars.user eq uid
        }.count()
    }
}