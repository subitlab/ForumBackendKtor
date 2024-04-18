package subit.database

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import subit.dataClasses.*

/**
 * 权限存储结构为使用一个ulong类型的id作为唯一索引。其索引前32位对应一个板块的bid, 后32位对应一个用户的uid。
 * permission字段表示id后32位所对用户在id前32位所对板块的权限。其为一个序列化到json字符串的map, 其key为权限名, value为权限值。
 * 若某一项权限为null, 则表示从父板块继承
 */
object PermissionDatabase: DataAccessObject<PermissionDatabase.Permissions>(Permissions)
{
    object Permissions: IdTable<ULong>("permissions")
    {
        override val id: Column<EntityID<ULong>> = blockUserId("id").uniqueIndex().entityId()
        val read: Column<PermissionLevel> = enumeration("read", PermissionLevel::class).default(PermissionLevel.NORMAL)
        val post: Column<PermissionLevel> = enumeration("post", PermissionLevel::class).default(PermissionLevel.NORMAL)
        val delete: Column<PermissionLevel> = enumeration("delete", PermissionLevel::class).default(PermissionLevel.NORMAL)
        val anonymous: Column<PermissionLevel> = enumeration("anonymous", PermissionLevel::class).default(PermissionLevel.NORMAL)
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private fun deserializePermission(row: ResultRow): PermissionGroup = PermissionGroup(
        read = row[Permissions.read],
        post = row[Permissions.post],
        delete = row[Permissions.delete],
        anonymous = row[Permissions.anonymous]
    )

    /**
     * 设置一个用户在一个板块的权限
     */
    suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionGroup) = query()
    {
        val id = BlockUserId(uid = uid, bid = bid)
        if (select { Permissions.id eq id.raw }.any()) update({ Permissions.id eq id.raw })
        {
            it[Permissions.read] = permission.read
            it[Permissions.post] = permission.post
            it[Permissions.delete] = permission.delete
            it[Permissions.anonymous] = permission.anonymous
        }
        else insert()
        {
            it[Permissions.id] = id.raw
            it[Permissions.read] = permission.read
            it[Permissions.post] = permission.post
            it[Permissions.delete] = permission.delete
            it[Permissions.anonymous] = permission.anonymous
        }
    }

    /**
     * 获得存储的原始权限信息, 若某一项权限为null, 则表示从父板块继承
     */
    private suspend fun getRawPermission(bid: BlockId, user: UserId): PermissionGroup = query()
    {
        val id = BlockUserId(uid = user, bid = bid).raw
        select { Permissions.id eq id }.firstOrNull()?.let(::deserializePermission) // 这里若找不到这个用户在这个数据库中的权限记录, 说明这个用户在这个数据库里没有被更改过权限, 则去查找这个数据库的默认权限
        ?: PermissionGroup.DEFAULT // 没有就返回默认值即全是NORMAL
    }

    /**
     * 获得最终生效的权限信息, 会从这个板块开始一直向上查找所有父板块的权限, 合并为最终生效权限, 具体生效规则见[PermissionLevel.getEffectivePermission]
     */
    suspend fun getPermission(bid0: BlockId, user: UserId): PermissionGroup = query()
    {
        var bid = bid0
        var group = getRawPermission(bid, user)
        while (true)
        {
            bid = BlockDatabase.getBlock(bid).parent ?: break
            group = PermissionGroup.getEffectivePermission(group, getRawPermission(bid, user))
        }
        group
    }

    suspend fun UserFull?.getPermission(bid: BlockId): PermissionGroup =
        this?.let { getPermission(bid, it.id) } ?: PermissionGroup.ANONYMOUS

    suspend fun PermissionGroup.canRead(post: PostInfo): Boolean
    {
        return if (post.state == PostState.NORMAL)
            read >= BlockDatabase.getBlock(post.block).reading
        else
            read >= maxOf(PermissionLevel.ADMIN, BlockDatabase.getBlock(post.block).reading)
    }

    suspend fun UserFull?.canRead(block: BlockId): Boolean = getPermission(block).read >= BlockDatabase.getBlock(block).reading
    suspend fun UserFull?.canRead(post: PostInfo): Boolean = getPermission(post.block).canRead(post)
    suspend fun UserFull?.canPost(block: BlockId): Boolean =
        getPermission(block).post >= BlockDatabase.getBlock(block).posting

    suspend fun UserFull?.canDelete(post: PostInfo): Boolean = getPermission(post.block).delete.let {
        it >= BlockDatabase.getBlock(post.block).posting &&
        (it >= PermissionLevel.ADMIN || post.author == this?.id)
    }

    suspend fun UserFull?.canAnonymous(block: BlockId): Boolean =
        getPermission(block).anonymous >= BlockDatabase.getBlock(block).anonymous

    suspend fun UserFull?.canComment(post: PostInfo): Boolean =
        getPermission(post.block).post >= BlockDatabase.getBlock(post.block).commenting
}