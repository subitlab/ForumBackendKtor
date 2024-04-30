package subit.database.sqlImpl

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import subit.dataClasses.*
import subit.database.Blocks
import subit.database.Permissions
import subit.database.Users

/**
 * 权限存储结构为使用一个ulong类型的id作为唯一索引。其索引前32位对应一个板块的bid, 后32位对应一个用户的uid。
 * permission字段表示id后32位所对用户在id前32位所对板块的权限。其为一个序列化到json字符串的map, 其key为权限名, value为权限值。
 * 若某一项权限为null, 则表示从父板块继承
 */
class PermissionsImpl: DaoSqlImpl<PermissionsImpl.PermissionTable>(PermissionTable), Permissions, KoinComponent
{
    private val blocks: Blocks by inject()
    private val users: Users by inject()

    object PermissionTable: IdTable<ULong>("permissions")
    {
        override val id: Column<EntityID<ULong>> = blockUserId("id").entityId()
        val permission: Column<PermissionLevel> = enumeration("permission", PermissionLevel::class).default(PermissionLevel.NORMAL)
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    /**
     * 设置一个用户在一个板块的权限
     */
    override suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionLevel): Unit = query()
    {
        val id = BlockUserId(uid = uid, bid = bid)
        if (select { PermissionTable.id eq id.raw }.any()) update({ PermissionTable.id eq id.raw })
        {
            it[PermissionTable.permission] = permission
        }
        else insert()
        {
            it[PermissionTable.id] = id.raw
            it[PermissionTable.permission] = permission
        }
    }

    /**
     * 获得存储的原始权限信息, 可能被父板块覆盖
     */
    private suspend fun getRawPermission(bid: BlockId, user: UserId): PermissionLevel = query()
    {
        val id = BlockUserId(uid = user, bid = bid).raw
        select { PermissionTable.id eq id }.firstOrNull()?.getOrNull(permission) // 这里若找不到这个用户在这个数据库中的权限记录, 说明这个用户在这个数据库里没有被更改过权限, 则去查找这个数据库的默认权限
        ?: PermissionLevel.NORMAL // 没有就返回默认值即全是NORMAL
    }

    /**
     * 获得最终生效的权限信息, 会从这个板块开始一直向上查找所有父板块的权限, 合并为最终生效权限, 具体生效规则见[PermissionLevel.getEffectivePermission]
     */
    override suspend fun getPermission(bid0: BlockId, user: UserId): PermissionLevel = query()
    {
        var bid = bid0
        var permission = getRawPermission(bid, user)
        val userFull = users.getUser(user) ?: return@query PermissionLevel.NORMAL
        while (true)
        {
            bid = blocks.getBlock(bid)?.parent ?: break
            permission = PermissionLevel.getEffectivePermission(permission, getRawPermission(bid, user))
        }
        return@query maxOf(permission,userFull.permission)
    }
}