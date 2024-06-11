package subit.database

import io.ktor.server.application.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.router.Context
import subit.utils.HttpStatus
import subit.utils.respond

interface Permissions
{
    suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionLevel)

    /**
     * 获得最终生效的权限信息, 会从这个板块开始一直向上查找所有父板块的权限, 合并为最终生效权限,
     * 具体生效规则见[PermissionLevel.getEffectivePermission]
     */
    suspend fun getPermission(block: BlockId, user: UserId): PermissionLevel
}

inline fun <reified T> Context.checkPermission(user: UserFull? = getLoginUser(), body: CheckPermissionContext.()->T): T =
    CheckPermissionContext(this, user).body()

data class CheckPermissionContext(val context: Context, val user: UserFull?): KoinComponent
{
    private val permissions = get<Permissions>()
    private val blocks = get<Blocks>()

    private suspend fun getPermission(bid: BlockId): PermissionLevel =
        user?.let { permissions.getPermission(bid, it.id) } ?: PermissionLevel.NORMAL

    private suspend fun finish(status: HttpStatus)
    {
        context.call.respond(status)
        context.finish()
    }

    private suspend fun hasAdminIn(block: BlockId): Boolean =
        user != null && (getPermission(block) >= PermissionLevel.ADMIN)
    suspend fun checkHasAdminIn(block: BlockId) = if (!hasAdminIn(block)) finish(HttpStatus.Forbidden) else Unit
    suspend fun canRead(block: BlockId): Boolean = getPermission(block).let()
    {
        val blockFull = blocks.getBlock(block) ?: return false
        return !(!hasAdminIn(block) && it < blockFull.reading)
    }
    suspend fun checkCanRead(block: BlockId) = getPermission(block).let()
    {
        val blockFull = blocks.getBlock(block) ?: return finish(HttpStatus.NotFound)
        if (!hasAdminIn(block) && it < blockFull.reading) return finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanRead(post: PostInfo)
    {
        if (post.state == State.NORMAL) checkCanRead(post.block)
        else if (user == null || user.permission < PermissionLevel.ADMIN) finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanPost(block: BlockId)
    {
        val blockFull = blocks.getBlock(block) ?: return finish(HttpStatus.NotFound)
        if (getPermission(block) < blockFull.posting) return finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanDelete(post: PostInfo) = getPermission(post.block).let {
        if (!hasAdminIn(post.block) && post.author != user?.id) finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanAnonymous(block: BlockId) = blocks.getBlock(block)?.let {
        if (getPermission(block) < it.anonymous) finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanComment(post: PostInfo) = blocks.getBlock(post.block)?.let {
        if (getPermission(post.block) < it.commenting) finish(HttpStatus.Forbidden)
    }

    fun hasGlobalAdmin() = user!=null && user.permission >= PermissionLevel.ADMIN
    suspend fun checkHasGlobalAdmin() = if (!hasGlobalAdmin()) finish(HttpStatus.Forbidden) else Unit
}