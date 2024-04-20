package subit.database

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import subit.dataClasses.PrivateChat
import subit.dataClasses.UserId

object PrivateChatDatabase: DataAccessObject<PrivateChatDatabase.PrivateChats>(PrivateChats)
{
    object PrivateChats: Table("private_chats")
    {
        val from = reference("from", UserDatabase.Users).index()
        val to = reference("to", UserDatabase.Users).index()
        val time = timestamp("time").index().defaultExpression(CurrentTimestamp())
        val content = text("content")
    }

    private fun deserialize(row: ResultRow) = PrivateChat(
        from = row[PrivateChats.from].value,
        to = row[PrivateChats.to].value,
        time = row[PrivateChats.time].toEpochMilli(),
        content = row[PrivateChats.content]
    )

    suspend fun addPrivateChat(from: UserId, to: UserId, content: String) = query()
    {
        insert {
            it[this.from] = from
            it[this.to] = to
            it[this.content] = content
        }
    }
}