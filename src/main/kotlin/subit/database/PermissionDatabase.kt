package subit.database

import io.ktor.server.application.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.router.Context
import subit.utils.HttpStatus
import subit.utils.respond

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
        val permission: Column<PermissionLevel> = enumeration("permission", PermissionLevel::class).default(PermissionLevel.NORMAL)
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    /**
     * 设置一个用户在一个板块的权限
     */
    suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionLevel) = query()
    {
        val id = BlockUserId(uid = uid, bid = bid)
        if (select { Permissions.id eq id.raw }.any()) update({ Permissions.id eq id.raw })
        {
            it[Permissions.permission] = permission
        }
        else insert()
        {
            it[Permissions.id] = id.raw
            it[Permissions.permission] = permission
        }
    }

    /**
     * 获得存储的原始权限信息, 可能被父板块覆盖
     */
    private suspend fun getRawPermission(bid: BlockId, user: UserId): PermissionLevel = query()
    {
        val id = BlockUserId(uid = user, bid = bid).raw
        select { Permissions.id eq id }.firstOrNull()?.getOrNull(permission) // 这里若找不到这个用户在这个数据库中的权限记录, 说明这个用户在这个数据库里没有被更改过权限, 则去查找这个数据库的默认权限
        ?: PermissionLevel.NORMAL // 没有就返回默认值即全是NORMAL
    }

    /**
     * 获得最终生效的权限信息, 会从这个板块开始一直向上查找所有父板块的权限, 合并为最终生效权限, 具体生效规则见[PermissionLevel.getEffectivePermission]
     */
    suspend fun getPermission(bid0: BlockId, user: UserId): PermissionLevel = query()
    {
        var bid = bid0
        var permission = getRawPermission(bid, user)
        val userFull = UserDatabase.getUser(user) ?: return@query PermissionLevel.NORMAL
        while (true)
        {
            bid = BlockDatabase.getBlock(bid)?.parent ?: break
            permission = PermissionLevel.getEffectivePermission(permission, getRawPermission(bid, user))
        }
        return@query maxOf(permission,userFull.permission)
    }
}

//fun Route.checkPermission(body: (UserFull)->Boolean) = intercept(ApplicationCallPipeline.Call) { checkPermission(body) }
inline fun <reified T> Context.checkPermission(user: UserFull? = getLoginUser(), body: CheckPermissionContext.()->T): T
{
    val checkPermissionContext = CheckPermissionContext(this, user)
    return checkPermissionContext.body()
}

data class CheckPermissionContext(val context: Context, val user: UserFull?)
{
    private suspend fun getPermission(bid: BlockId): PermissionLevel =
        user?.let { PermissionDatabase.getPermission(bid, it.id) } ?: PermissionLevel.NORMAL

    private suspend fun finish(status: HttpStatus)
    {
        context.call.respond(status)
        context.finish()
    }

    suspend fun hasAdminIn(block: BlockId): Boolean =
        user != null && (getPermission(block) >= PermissionLevel.ADMIN)
    suspend fun checkHasAdminIn(block: BlockId) = if (!hasAdminIn(block)) finish(HttpStatus.Forbidden) else Unit
    suspend fun checkCanRead(block: BlockId) = getPermission(block).let()
    {
        val blockFull = BlockDatabase.getBlock(block) ?: return finish(HttpStatus.NotFound)
        if (!hasAdminIn(block) && it < blockFull.reading) return finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanRead(post: PostInfo)
    {
        if (post.state == State.NORMAL) checkCanRead(post.block)
        else if (user == null || user.permission < PermissionLevel.ADMIN) finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanPost(block: BlockId)
    {
        val blockFull = BlockDatabase.getBlock(block) ?: return finish(HttpStatus.NotFound)
        if (getPermission(block) < blockFull.posting) return finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanDelete(post: PostInfo) = getPermission(post.block).let {
        if (!hasAdminIn(post.block) && post.author != user?.id) finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanAnonymous(block: BlockId) = BlockDatabase.getBlock(block)?.let {
        if (getPermission(block) < it.anonymous) finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanComment(post: PostInfo) = BlockDatabase.getBlock(post.block)?.let {
        if (getPermission(post.block) < it.commenting) finish(HttpStatus.Forbidden)
    }

    fun hasGlobalAdmin() = user!=null && user.permission >= PermissionLevel.ADMIN
    suspend fun checkHasGlobalAdmin() = if (!hasGlobalAdmin()) finish(HttpStatus.Forbidden) else Unit
}