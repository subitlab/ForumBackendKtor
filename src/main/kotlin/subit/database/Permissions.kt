@file:Suppress("unused")
package subit.database

import io.ktor.server.application.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.dataClasses.State.DELETED
import subit.dataClasses.State.NORMAL
import subit.router.Context
import subit.utils.HttpStatus
import subit.utils.respond

interface Permissions
{
    suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionLevel)
    suspend fun getPermission(block: BlockId, user: UserId): PermissionLevel
}

inline fun <reified T> Context.checkPermission(
    user: UserFull? = getLoginUser(),
    body: CheckPermissionInContextScope.()->T
): T = CheckPermissionInContextScope(this, user).body()

inline fun <reified T> checkPermission(
    user: UserFull?,
    body: CheckPermissionScope.()->T
): T = CheckPermissionScope(user).body()

open class CheckPermissionScope(val user: UserFull?): KoinComponent
{
    protected val permissions = get<Permissions>()
    protected val blocks = get<Blocks>()
    protected val posts = get<Posts>()

    /**
     * 获取用户在某一个板块的权限等级
     */
    suspend fun getPermission(bid: BlockId): PermissionLevel =
        user?.let { permissions.getPermission(bid, it.id) } ?: PermissionLevel.NORMAL

    fun getGlobalPermission(): PermissionLevel =
        user?.permission ?: PermissionLevel.NORMAL

    suspend fun hasAdminIn(block: BlockId): Boolean =
        user != null && (getPermission(block) >= PermissionLevel.ADMIN)

    fun hasGlobalAdmin(): Boolean = user.hasGlobalAdmin()

    /// 可以看 ///

    suspend fun canRead(block: BlockFull): Boolean = when (block.state)
    {
        NORMAL  -> getPermission(block.id) < block.reading
        DELETED -> (getPermission(block.id) < block.reading) && user.hasGlobalAdmin()
    }

    suspend fun canRead(post: PostInfo): Boolean = when (post.state)
    {
        NORMAL  -> blocks.getBlock(post.block)?.let { canRead(it) } ?: false
        DELETED -> hasAdminIn(post.block)
    }

    suspend fun canRead(comment: Comment): Boolean = when (comment.state)
    {
        NORMAL  -> posts.getPost(comment.post)?.let { canRead(it) } ?: false
        DELETED -> posts.getPost(comment.post)?.let { hasAdminIn(it.block) } ?: false
    }

    /// 可以删除 ///

    suspend fun canDelete(post: PostInfo): Boolean = when (post.state)
    {
        NORMAL  -> post.author == user?.id || hasAdminIn(post.block)
        DELETED -> false
    }

    suspend fun canDelete(block: BlockFull): Boolean = when (block.state)
    {
        NORMAL  -> hasAdminIn(block.id)
        DELETED -> false
    }

    suspend fun canDelete(comment: Comment): Boolean = when (comment.state)
    {
        NORMAL  -> comment.author == user?.id || posts.getPost(comment.post)?.let { hasAdminIn(it.block) } ?: false
        DELETED -> false
    }

    /// 可以评论 ///

    suspend fun canComment(post: PostInfo): Boolean = when (post.state)
    {
        NORMAL  -> blocks.getBlock(post.block)?.let { getPermission(post.block) >= it.commenting } ?: false
        DELETED -> false
    }

    /// 可以发贴 ///

    suspend fun canPost(block: BlockFull): Boolean = when (block.state)
    {
        NORMAL  -> getPermission(block.id) < block.posting
        DELETED -> (getPermission(block.id) < block.posting) && user.hasGlobalAdmin()
    }

    /// 可以匿名 ///

    suspend fun canAnonymous(block: BlockFull): Boolean = when (block.state)
    {
        NORMAL  -> getPermission(block.id) < block.anonymous
        DELETED -> (getPermission(block.id) < block.anonymous) && user.hasGlobalAdmin()
    }

    /// 修改他人权限 ///

    /**
     * 检查是否可以修改权限
     * @param block 修改权限的板块, 为null表示全局权限
     * @param other 被修改权限的用户, 可以是自己
     * @param permission 目标权限(修改后的权限)
     */
    suspend fun canChangePermission(block: BlockFull?, other: UserFull, permission: PermissionLevel): Boolean
    {
        // 如果在尝试修改自己的权限
        if (other.id == user?.id)
        {
            // 如果尝试修改自己的全局权限, 要有全局管理员且目标权限比当前权限低
            if (block == null)
                return getGlobalPermission() >= maxOf(permission, PermissionLevel.ADMIN)

            // 如果尝试修改自己在某板块的权限
            // 如果目标权限比现在低, 直接通过
            if (permission < getPermission(block.id)) return true

            // 如果目标权限比现在高
            // 要么其父板块权限高于目标权限
            block.parent?.let { parent ->
                if (getPermission(parent) > permission) return true
            }
            // 要么其拥有全局管理员
            return hasGlobalAdmin()
        }
        if (block == null)
        {
            return hasGlobalAdmin() && other.permission < getGlobalPermission() && permission < getGlobalPermission()
        }
        val selfPermission = getPermission(block.id)
        if (selfPermission < PermissionLevel.ADMIN) return false
        val otherPermission = checkPermission(other) { getPermission(block.id) }
        return selfPermission > otherPermission && selfPermission > permission
    }
}

class CheckPermissionInContextScope(val context: Context, user: UserFull?): CheckPermissionScope(user)
{
    /**
     * 结束请求
     */
    private suspend fun finish(status: HttpStatus)
    {
        context.call.respond(status)
        context.finish()
    }

    suspend fun checkHasAdminIn(block: BlockId) = if (!hasAdminIn(block)) finish(HttpStatus.Forbidden) else Unit

    suspend fun checkHasGlobalAdmin() = if (!hasGlobalAdmin()) finish(HttpStatus.Forbidden) else Unit

    /// 可以看 ///

    suspend fun checkCanRead(block: BlockFull)
    {
        if (!canRead(block))
            finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanRead(post: PostInfo)
    {
        if (!canRead(post))
            finish(HttpStatus.Forbidden)
    }

    suspend fun checkCanRead(comment: Comment)
    {
        if (!canRead(comment))
            finish(HttpStatus.Forbidden)
    }

    /// 可以删除 ///

    suspend fun checkCanDelete(post: PostInfo)
    {
        if (!canDelete(post))
            finish(HttpStatus.Forbidden)
    }

    /// 可以评论 ///

    suspend fun checkCanComment(post: PostInfo)
    {
        if (!canComment(post))
            finish(HttpStatus.Forbidden)
    }

    /// 可以发贴 ///

    suspend fun checkCanPost(block: BlockFull)
    {
        if (!canPost(block))
            finish(HttpStatus.Forbidden)
    }

    /// 可以匿名 ///

    suspend fun checkCanAnonymous(block: BlockFull)
    {
        if (!canAnonymous(block))
            finish(HttpStatus.Forbidden)
    }

    suspend fun checkChangePermission(block: BlockFull?, other: UserFull, permission: PermissionLevel)
    {
        /**
         * 详见[CheckPermissionScope.canChangePermission]
         *
         * 这里在其基础上将返回true改为不做任何操作, 返回false改为结束请求, 并返回403及详细说明
         */
        if (other.id == user?.id)
        {
            // 如果尝试修改自己的全局权限, 要有全局管理员且目标权限比当前权限低
            if (block == null)
            {
                if (getGlobalPermission() >= maxOf(permission, PermissionLevel.ADMIN)) return
                else return finish(
                    HttpStatus.Forbidden.copy(
                        message = "修改自己的全局权限要求拥有全局管理员权限, 且目标权限不得高于当前权限"
                    )
                )
            }

            // 如果尝试修改自己在某板块的权限
            // 如果目标权限比现在低, 直接通过
            if (permission <= getPermission(block.id)) return

            // 如果目标权限比现在高
            // 要么其父板块权限高于目标权限
            block.parent?.let { parent ->
                if (getPermission(parent) >= permission) return
            }
            // 要么其拥有全局管理员
            if (hasGlobalAdmin()) return

            // 返回消息, 根据有没有父板块返回不同的消息
            if (block.parent != null) finish(
                HttpStatus.Forbidden.copy(
                    message = "修改自己在板块${block.name}的权限要求在此板块的权限或在父板块的权限不低于目标权限, 或拥有全局管理员权限"
                )
            )
            else finish(
                HttpStatus.Forbidden.copy(
                    message = "修改自己在板块${block.name}的权限要求在此板块的权限不低于目标权限, 或拥有全局管理员权限"
                )
            )
            return
        }
        if (block == null)
        {
            if(hasGlobalAdmin() && other.permission < getGlobalPermission() && permission < getGlobalPermission())
                return
            else return finish(
                HttpStatus.Forbidden.copy(
                    message = "修改他人的全局权限要求拥有全局管理员权限, 且目标用户修改前后的权限都低于自己的全局权限"
                )
            )
        }
        val selfPermission = getPermission(block.id)
        if (selfPermission < PermissionLevel.ADMIN) return finish(
            HttpStatus.Forbidden.copy(
                message = "修改他人在板块${block.name}的权限要求拥有该板块管理员权限"
            )
        )
        val otherPermission = checkPermission(other) { getPermission(block.id) }
        if (selfPermission > otherPermission && selfPermission > permission)
            return
        else return finish(
            HttpStatus.Forbidden.copy(
                message = "修改他人在板块${block.name}的权限要求拥有该板块管理员权限, 且目标用户修改前后的权限都低于自己的权限"
            )
        )
    }
}