package subit.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.javatime.timestamp
import subit.dataClasses.Prohibit
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserId
import java.time.Instant

object ProhibitDatabase: DataAccessObject<ProhibitDatabase.Prohibits>(Prohibits)
{
    object Prohibits: Table("prohibits")
    {
        val user = reference("user", UserDatabase.Users).uniqueIndex()
        val time = timestamp("time")
        val reason = text("reason")
        val operator = reference("operator", UserDatabase.Users).index()
    }

    private fun deserialize(row: ResultRow) = Prohibit(
        row[Prohibits.user].value,
        row[Prohibits.time].toEpochMilli(),
        row[Prohibits.reason],
        row[Prohibits.operator].value
    )

    suspend fun addProhibit(prohibit: Prohibit) = query()
    {
        insert {
            it[user] = prohibit.user
            it[this.time] = Instant.ofEpochMilli(prohibit.time)
            it[this.reason] = prohibit.reason
            it[this.operator] = prohibit.operator
        }
    }

    suspend fun removeProhibit(uid: UserId) = query()
    {
        deleteWhere { user eq uid }
    }

    /**
     * 检查用户是否被禁止, true代表被封禁
     */
    suspend fun checkProhibit(uid: UserId): Boolean = query()
    {
        deleteWhere { time lessEq Instant.now() }
        select { user eq uid }.count() > 0
    }

    suspend fun getProhibitList(begin: Long, count: Int): Slice<Prohibit> = query()
    {
        Prohibits.selectAll().asSlice(begin, count).map(::deserialize)
    }
}