package subit.database.sqlImpl

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.koin.core.component.KoinComponent
import subit.dataClasses.PostId
import subit.dataClasses.UserId
import subit.database.Likes

class LikesImpl: DaoSqlImpl<LikesImpl.LikesTable>(LikesTable), Likes, KoinComponent
{
    object LikesTable: Table("likes")
    {
        val user = reference("user", UsersImpl.UserTable).index()
        val post = reference("post", PostsImpl.PostsTable).index()
        // true为点赞, false为点踩
        val like = bool("like").index()
    }

    override suspend fun like(uid: UserId, pid: PostId, like: Boolean): Unit = query()
    {
        insert {
            it[user] = uid
            it[post] = pid
            it[table.like] = like
        }
    }

    override suspend fun unlike(uid: UserId, pid: PostId): Unit = query()
    {
        deleteWhere {
            (user eq uid) and (post eq pid)
        }
    }

    override suspend fun getLike(uid: UserId, pid: PostId): Boolean = query()
    {
        select(like).where {
            (user eq uid) and (post eq pid)
        }.singleOrNull()?.get(like) ?: false
    }

    override suspend fun getLikes(post: PostId):Pair<Long,Long> = query()
    {
        val likes = selectAll().where { (LikesTable.post eq post) and (like eq true) }.count()
        val dislikes = selectAll().where { (LikesTable.post eq post) and (like eq false) }.count()
        likes to dislikes
    }
}