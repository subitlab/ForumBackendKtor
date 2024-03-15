package subit.database

import io.ktor.server.auth.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * 用户数据库数据类
 * @param id 用户ID
 * @param username 用户名(学生ID)
 * @param password 密码
 * @param email 邮箱
 * @param phone 电话
 * @param registrationTime 注册时间
 * @param introduction 个人简介
 * @param read 阅读权限
 * @param post 发帖权限
 * @param comment 评论权限
 * @param ask 提问权限
 * @param file 文件权限
 * @param delete 删除权限
 * @param anonymous 匿名权限
 */
data class UserFull(
    val id: ULong,
    val username: String,
    val password: String,
    val email: String = "",
    val phone: String = "",
    val registrationTime: LocalDateTime = LocalDateTime.now(),
    val introduction: String = "",
    val read: Permission = Permission.NORMAL,
    val post: Permission = Permission.NORMAL,
    val comment: Permission = Permission.NORMAL,
    val ask: Permission = Permission.NORMAL,
    val file: Permission = Permission.NORMAL,
    val delete: Permission = Permission.NORMAL,
    val anonymous: Permission = Permission.NORMAL,
):Principal

/**
 * 用户数据库交互类
 */
object UserDatabase: DatabaseController<UserDatabase.Users>(Users)
{
    /**
     * 用户信息表
     */
    object Users: IdTable<ULong>("users")
    {
        override val id: Column<EntityID<ULong>> = ulong("id").entityId()
        val username = varchar("username", 100).index()
        val password = text("password")
        val email = varchar("email", 100).uniqueIndex()
        val phone = varchar("phone", 100).default("").index()
        val registrationTime = datetime("registrationTime").defaultExpression(CurrentDateTime)
        val introduction = text("introduction").nullable().default(null)
        val read = enumeration<Permission>("read").default(Permission.NORMAL)
        val post = enumeration<Permission>("post").default(Permission.NORMAL)
        val comment = enumeration<Permission>("comment").default(Permission.NORMAL)
        val ask = enumeration<Permission>("ask").default(Permission.NORMAL)
        val file = enumeration<Permission>("file").default(Permission.NORMAL)
        val delete = enumeration<Permission>("delete").default(Permission.NORMAL)
        val anonymous = enumeration<Permission>("anonymous").default(Permission.NORMAL)
        override val primaryKey = PrimaryKey(id)
    }

}

/**
 * 权限枚举
 */
enum class Permission
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
        fun fromLevel(level: Int): Permission
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
    }
}