package subit.database.sqlImpl

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import subit.database.Whitelists

class WhitelistsImpl: DaoSqlImpl<WhitelistsImpl.WhitelistTable>(WhitelistTable), Whitelists
{
    object WhitelistTable: IdTable<String>("whitelist")
    {
        val email = varchar("email", 100).entityId()
        override val id: Column<EntityID<String>>
            get() = email
        override val primaryKey: PrimaryKey
            get() = PrimaryKey(email)
    }

    override suspend fun add(email: String): Unit = query()
    {
        insert {
            it[WhitelistTable.email] = email
        }
    }

    override suspend fun remove(email: String): Unit = query()
    {
        deleteWhere {
            WhitelistTable.email eq email
        }
    }

    override suspend fun isWhitelisted(email: String): Boolean = query()
    {
        select {
            WhitelistTable.email eq email
        }.count() > 0
    }

    override suspend fun getWhitelist(): List<String> = query()
    {
        selectAll().map { it[email].value }
    }
}