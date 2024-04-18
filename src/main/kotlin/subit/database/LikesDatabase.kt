package subit.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import subit.dataClasses.PostId
import subit.dataClasses.UserId

object LikesDatabase: DataAccessObject<LikesDatabase.Likes>(Likes)
{
    object Likes: Table("likes")
    {
        val user = reference("user", UserDatabase.Users).index()
        val post = reference("post", PostDatabase.Posts, ReferenceOption.CASCADE, ReferenceOption.CASCADE).index()
        // true为点赞, false为点踩
        val like = bool("like").index()
    }

    suspend fun like(uid: UserId, pid: PostId, like: Boolean) = query()
    {
        insert {
            it[user] = uid
            it[post] = pid
            it[Likes.like] = like
        }
    }

    suspend fun unlike(uid: UserId, pid: PostId) = query()
    {
        deleteWhere {
            (Likes.user eq uid) and (Likes.post eq pid)
        }
    }

    suspend fun getLike(uid: UserId, pid: PostId): Boolean = query()
    {
        select {
            (Likes.user eq uid) and (Likes.post eq pid)
        }.firstOrNull()?.get(like) ?: false
    }

    suspend fun getLikes(post: PostId):Pair<Long,Long> = query()
    {
        val likes = select { (Likes.post eq post) and (like eq true) }.count()
        val dislikes = select { (Likes.post eq post) and (like eq false) }.count()
        likes to dislikes
    }
}