package subit.database.sqlImpl

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import subit.dataClasses.*
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.Slice.Companion.singleOrNull
import subit.database.Blocks
import subit.database.Permissions
import subit.database.sqlImpl.PostsImpl.PostsTable

/**
 * 板块数据库交互类
 */
class BlocksImpl: DaoSqlImpl<BlocksImpl.BlocksTable>(BlocksTable), Blocks, KoinComponent
{
    private val permissions: Permissions by inject()

    object BlocksTable: IdTable<BlockId>("blocks")
    {
        override val id = blockId("id").autoIncrement().entityId()
        val name = varchar("name", 100).index()
        val description = text("description")
        val parent = reference("parent", BlocksTable, ReferenceOption.CASCADE, ReferenceOption.CASCADE).nullable()
            .default(null)
            .index()
        val creator = reference("creator", UsersImpl.UserTable).index()
        val state = enumerationByName<State>("state", 20).default(State.NORMAL)
        val posting = enumeration<PermissionLevel>("posting").default(PermissionLevel.NORMAL)
        val commenting = enumeration<PermissionLevel>("commenting").default(PermissionLevel.NORMAL)
        val reading = enumeration<PermissionLevel>("reading").default(PermissionLevel.NORMAL)
        val anonymous = enumeration<PermissionLevel>("anonymous").default(PermissionLevel.NORMAL)
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private fun deserializeBlock(row: ResultRow): BlockFull = BlockFull(
        id = row[BlocksTable.id].value,
        name = row[BlocksTable.name],
        description = row[BlocksTable.description],
        parent = row[BlocksTable.parent]?.value,
        creator = row[BlocksTable.creator].value,
        posting = row[BlocksTable.posting],
        commenting = row[BlocksTable.commenting],
        reading = row[BlocksTable.reading],
        anonymous = row[BlocksTable.anonymous],
        state = row[BlocksTable.state]
    )

    override suspend fun createBlock(
        name: String,
        description: String,
        parent: BlockId?,
        creator: UserId,
        postingPermission: PermissionLevel,
        commentingPermission: PermissionLevel,
        readingPermission: PermissionLevel,
        anonymousPermission: PermissionLevel
    ): BlockId = query()
    {
        insertAndGetId {
            it[BlocksTable.name] = name
            it[BlocksTable.description] = description
            it[BlocksTable.parent] = parent
            it[BlocksTable.creator] = creator
            it[posting] = postingPermission
            it[commenting] = commentingPermission
            it[reading] = readingPermission
            it[anonymous] = anonymousPermission
        }.value
    }

    override suspend fun setPermission(
        block: BlockId,
        posting: PermissionLevel?,
        commenting: PermissionLevel?,
        reading: PermissionLevel?,
        anonymous: PermissionLevel?
    ): Unit = query()
    {
        update({ id eq block })
        {
            if (posting != null) it[BlocksTable.posting] = posting
            if (commenting != null) it[BlocksTable.commenting] = commenting
            if (reading != null) it[BlocksTable.reading] = reading
            if (anonymous != null) it[BlocksTable.anonymous] = anonymous
        }
    }

    override suspend fun getBlock(block: BlockId): BlockFull? = query()
    {
        selectAll().where { id eq block }.singleOrNull()?.let(::deserializeBlock)
    }

    override suspend fun setState(block: BlockId, state: State): Unit = query()
    {
        update({ id eq block })
        {
            it[BlocksTable.state] = state
        }
    }

    override suspend fun getChildren(loginUser: UserId?, parent: BlockId?, begin: Long, count: Int) = query()
    {
        val permissionTable = (permissions as PermissionsImpl).table
        val additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? =
            if (loginUser != null) ({ permissionTable.user eq loginUser })
            else null
        BlocksTable.join(permissionTable, JoinType.LEFT, id, permissionTable.block, additionalConstraint)
            .select(id)
            .where { BlocksTable.parent eq parent }
            .andWhere { PostsTable.state eq State.NORMAL }
            .groupBy(id, reading)
            .having { (permissionTable.permission.max() greaterEq reading) or (reading lessEq PermissionLevel.NORMAL) }
            .orderBy(id, SortOrder.DESC)
            .asSlice(begin, count)
            .map { it[id].value }
    }

    override suspend fun searchBlock(loginUser: UserId?, key: String, begin: Long, count: Int): Slice<BlockId> = query()
    {
        val permissionTable = (permissions as PermissionsImpl).table
        val additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? =
            if (loginUser != null) ({ permissionTable.user eq loginUser })
            else null
        BlocksTable.join(permissionTable, JoinType.LEFT, id, permissionTable.block, additionalConstraint)
            .select(BlocksTable.columns)
            .where { (name like "%$key%") or (description like "%$key%") }
            .andWhere { PostsTable.state eq State.NORMAL }
            .groupBy(id, reading)
            .having { (permissionTable.permission.max() greaterEq reading) or (reading lessEq PermissionLevel.NORMAL) }
            .orderBy(id, SortOrder.DESC)
            .asSlice(begin, count)
            .map { it[id].value }
    }
}