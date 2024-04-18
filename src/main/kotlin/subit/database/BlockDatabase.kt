package subit.database

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import subit.dataClasses.*

/**
 * 板块数据库交互类
 */
object BlockDatabase: DataAccessObject<BlockDatabase.Blocks>(Blocks)
{
    object Blocks: IdTable<BlockId>("blocks")
    {
        override val id: Column<EntityID<BlockId>> = blockId("id").autoIncrement().entityId()
        val name = varchar("name", 100).index()
        val description = text("description")
        val parent = reference("parent", Blocks, ReferenceOption.CASCADE, ReferenceOption.CASCADE).nullable().default(null).index()
        val creator = reference("creator", UserDatabase.Users).index()
        val posting = enumeration<PermissionLevel>("posting").default(PermissionLevel.NORMAL)
        val commenting = enumeration<PermissionLevel>("commenting").default(PermissionLevel.NORMAL)
        val reading = enumeration<PermissionLevel>("reading").default(PermissionLevel.NORMAL)
        val anonymous = enumeration<PermissionLevel>("anonymous").default(PermissionLevel.NORMAL)
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private fun deserializeBlock(row: ResultRow): BlockFull = BlockFull(
        id = row[Blocks.id].value,
        name = row[Blocks.name],
        description = row[Blocks.description],
        parent = row[Blocks.parent]?.value,
        creator = row[Blocks.creator].value,
        posting = row[Blocks.posting],
        commenting = row[Blocks.commenting],
        reading = row[Blocks.reading],
        anonymous = row[Blocks.anonymous]
    )

    suspend fun createBlock(
        name: String,
        description: String,
        parent: BlockId?,
        creator: UserId,
        postingPermission: PermissionLevel = PermissionLevel.NORMAL,
        commentingPermission: PermissionLevel = PermissionLevel.NORMAL,
        readingPermission: PermissionLevel = PermissionLevel.NORMAL,
        anonymousPermission: PermissionLevel = PermissionLevel.NORMAL
    ) = query()
    {
        insert {
            it[Blocks.name] = name
            it[Blocks.description] = description
            it[Blocks.parent] = parent
            it[Blocks.creator] = creator
            it[Blocks.posting] = postingPermission
            it[Blocks.commenting] = commentingPermission
            it[Blocks.reading] = readingPermission
            it[Blocks.anonymous] = anonymousPermission
        }
    }

    suspend fun setPermission(
        block: BlockId,
        posting: PermissionLevel?,
        commenting: PermissionLevel?,
        reading: PermissionLevel?,
        anonymous: PermissionLevel?
    ) = query()
    {
        update({ Blocks.id eq block })
        {
            if (posting != null) it[Blocks.posting] = posting
            if (commenting != null) it[Blocks.commenting] = commenting
            if (reading != null) it[Blocks.reading] = reading
            if (anonymous != null) it[Blocks.anonymous] = anonymous
        }
    }

    suspend fun getBlock(block: BlockId): BlockFull = query()
    {
        select { Blocks.id eq block }.first().let(::deserializeBlock)
    }
}