package subit.database

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

object WhitelistDatabase: DatabaseController<WhitelistDatabase.Whitelist>(Whitelist)
{
    object Whitelist: IdTable<String>("whitelist")
    {
        val email = varchar("email", 100).entityId()
        override val id: Column<EntityID<String>>
            get() = email
        override val primaryKey: PrimaryKey
            get() = PrimaryKey(email)
    }
}