package subit.database

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

object WhitelistDatabase: DataAccessObject<WhitelistDatabase.Whitelist>(Whitelist)
{
    object Whitelist: IdTable<String>("whitelist")
    {
        val email = varchar("email", 100).entityId()
        override val id: Column<EntityID<String>>
            get() = email
        override val primaryKey: PrimaryKey
            get() = PrimaryKey(email)
    }

    suspend fun add(email: String) = query()
    {
        insert {
            it[Whitelist.email] = email
        }
    }

    suspend fun remove(email: String) = query()
    {
        deleteWhere {
            Whitelist.email eq email
        }
    }

    suspend fun isWhitelisted(email: String): Boolean = query()
    {
        select {
            Whitelist.email eq email
        }.count() > 0
    }

    suspend fun getWhitelist(): List<String> = query()
    {
        selectAll().map { it[email].value }
    }
}