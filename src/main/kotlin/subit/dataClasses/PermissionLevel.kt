package subit.dataClasses

import kotlinx.serialization.Serializable

@Serializable
enum class PermissionLevel
{
    BANNED,
    NORMAL,
    ADMIN,
    SUPER_ADMIN,
    ROOT;

    @Suppress("unused")
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
        @Suppress("unused")
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
        private fun getEffectivePermission(a: PermissionLevel, b: PermissionLevel): PermissionLevel
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
        @Deprecated(
            message = "权限结构变更, 计算权限时不再需要权限合并, 故该方法废弃.",
            level = DeprecationLevel.ERROR,
            replaceWith = ReplaceWith("")
        )
        fun getEffectivePermission(vararg permissions: PermissionLevel): PermissionLevel =
            permissions.reduce(Companion::getEffectivePermission)
    }
}