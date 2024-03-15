package subit.database

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * 收藏数据库交互类
 */
object StarDatabase: DatabaseController<StarDatabase.Stars>(Stars)
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
        val post = reference("post", PostDatabase.Posts,ReferenceOption.CASCADE,ReferenceOption.SET_NULL).nullable().default(null)

        /**
         * 收藏的时间
         */
        val time = datetime("time").defaultExpression(CurrentDateTime).index()
    }
}