package subit.database.sqlImpl

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.koin.core.component.KoinComponent
import subit.dataClasses.BlockId
import subit.dataClasses.PermissionLevel
import subit.dataClasses.Slice.Companion.singleOrNull
import subit.dataClasses.UserId
import subit.database.Permissions

class PermissionsImpl: DaoSqlImpl<PermissionsImpl.PermissionTable>(PermissionTable), Permissions, KoinComponent
{
    object PermissionTable: Table("permissions")
    {
        val user = reference("user", UsersImpl.UserTable).index()
        val block = reference("block", BlocksImpl.BlocksTable).index()
        val permission = enumeration("permission", PermissionLevel::class).default(PermissionLevel.NORMAL)
    }

    /**
     * 设置一个用户在一个板块的权限
     */
    override suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionLevel): Unit = query()
    {
        if (permission == PermissionLevel.NORMAL)
        {
            deleteWhere { (user eq uid) and (block eq bid) }
            return@query
        }
        val count = update({ (user eq uid) and (block eq bid) })
        {
            it[PermissionTable.permission] = permission
        }

        if (count > 0) return@query

        // 如果没有更新到任何数据, 说明这个权限不存在, 需要插入
        insert()
        {
            it[user] = uid
            it[block] = bid
            it[PermissionTable.permission] = permission
        }
    }

    override suspend fun getPermission(block: BlockId, user: UserId): PermissionLevel = query()
    {
        select(permission).where {
            (PermissionTable.user eq user) and (PermissionTable.block eq block)
        }.singleOrNull()?.getOrNull(permission) ?: PermissionLevel.NORMAL
    }
}