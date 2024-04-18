package subit.dataClasses

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import subit.database.PermissionDatabase

/**
 * @param read 阅读权限
 * @param post 发帖权限
 * @param file 文件权限
 * @param delete 删除权限
 * @param anonymous 匿名权限
 */
@Serializable
data class PermissionGroup(
    val read: PermissionLevel,
    val post: PermissionLevel,
    val delete: PermissionLevel,
    val anonymous: PermissionLevel,
)
{
    /**
     * 检查是否有资格修改某个权限
     * 有资格的条件是：对于某一项权限, 要么不进行修改, 要么修改后的权限不高于自己的权限, 且自己的权限不低于ADMIN
     * @param permission 想要修改的权限
     */
    fun canChange(permission: PermissionGroupForOperation): Boolean =
        (permission.read == null || (read >= permission.read && read >= PermissionLevel.ADMIN)) &&
        (permission.post == null || (post >= permission.post && post >= PermissionLevel.ADMIN)) &&
        (permission.delete == null || (delete >= permission.delete && delete >= PermissionLevel.ADMIN)) &&
        (permission.anonymous == null || (anonymous >= permission.anonymous && anonymous >= PermissionLevel.ADMIN))

    fun toChangePermissionOperator() = PermissionGroupForOperation(read, post, delete, anonymous)

    companion object
    {
        /**
         * 将每一项权限分别按照[PermissionLevel.getEffectivePermission]的规则进行合并
         */
        fun getEffectivePermission(a: PermissionGroup, b: PermissionGroup): PermissionGroup
        {
            return PermissionGroup(
                read = PermissionLevel.getEffectivePermission(a.read, b.read),
                post = PermissionLevel.getEffectivePermission(a.post, b.post),
                delete = PermissionLevel.getEffectivePermission(a.delete, b.delete),
                anonymous = PermissionLevel.getEffectivePermission(a.anonymous, b.anonymous)
            )
        }

        /**
         * 用户默认权限
         */
        val DEFAULT = PermissionGroup(PermissionLevel.NORMAL, PermissionLevel.NORMAL, PermissionLevel.NORMAL, PermissionLevel.NORMAL)
        /**
         * 未登录用户权限
         */
        val ANONYMOUS = PermissionGroup(PermissionLevel.NORMAL, PermissionLevel.BANNED, PermissionLevel.BANNED, PermissionLevel.BANNED)
    }
}

@Serializable
data class PermissionGroupForOperation(
    val read: PermissionLevel? = null,
    val post: PermissionLevel? = null,
    val delete: PermissionLevel? = null,
    val anonymous: PermissionLevel? = null
)
{
    constructor(row: ResultRow): this(
        read = row[PermissionDatabase.Permissions.read],
        post = row[PermissionDatabase.Permissions.post],
        delete = row[PermissionDatabase.Permissions.anonymous],
        anonymous = row[PermissionDatabase.Permissions.anonymous]
    )
}

@Serializable
enum class PermissionLevel
{
    BANNED,
    NORMAL,
    ADMIN,
    SUPER_ADMIN,
    ROOT;

    fun toLevel() = when (this)
    {
        BANNED      -> 0
        NORMAL      -> 1
        ADMIN       -> 2
        SUPER_ADMIN -> 3
        ROOT        -> 4
    }

    companion object
    {
        fun fromLevel(level: Int): PermissionLevel
        {
            return when (level)
            {
                0    -> BANNED
                1    -> NORMAL
                2    -> ADMIN
                3    -> SUPER_ADMIN
                4    -> ROOT
                else -> NORMAL
            }
        }
        /**
         * 获取生效的权限, 当用户的某一项权限有两个的时候, 获取生效的权限
         * 优先级是 ROOT > BANNED > SUPER_ADMIN > ADMIN > NORMAL
         * 即当前传入的两个权限中有ROOT, 则返回ROOT, 有BANNED, 则返回BANNED, 以此类推
         */
        fun getEffectivePermission(a: PermissionLevel, b: PermissionLevel): PermissionLevel
        {
            return when
            {
                a == ROOT || b == ROOT -> ROOT
                a == BANNED || b == BANNED -> BANNED
                a == SUPER_ADMIN || b == SUPER_ADMIN -> SUPER_ADMIN
                a == ADMIN || b == ADMIN -> ADMIN
                else -> NORMAL
            }
        }
        /**
         * 获取生效的权限, 当用户的某一项权限有两个的时候, 获取生效的权限
         * 优先级是 ROOT > BANNED > SUPER_ADMIN > ADMIN > NORMAL
         * 即当前传入的两个权限中有ROOT, 则返回ROOT, 有BANNED, 则返回BANNED, 以此类推
         */
        fun getEffectivePermission(vararg permissions: PermissionLevel): PermissionLevel =
            permissions.reduce(Companion::getEffectivePermission)
    }
}