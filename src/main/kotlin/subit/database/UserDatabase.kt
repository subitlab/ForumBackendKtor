package subit.database

import io.ktor.server.auth.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import subit.JWTAuth
import java.time.LocalDateTime

/**
 * 用户数据库数据类
 * @param id 用户ID
 * @param username 用户名
 * @param email 邮箱(唯一)
 * @param registrationTime 注册时间
 * @param figure 头像
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
    val id: Long,
    val username: String,
    val email: String = "",
    val registrationTime: LocalDateTime = LocalDateTime.now(),
    val figure: String? = null,
    val introduction: String = "",
    val read: PermissionLevel = PermissionLevel.NORMAL,
    val post: PermissionLevel = PermissionLevel.NORMAL,
    val comment: PermissionLevel = PermissionLevel.NORMAL,
    val ask: PermissionLevel = PermissionLevel.NORMAL,
    val file: PermissionLevel = PermissionLevel.NORMAL,
    val delete: PermissionLevel = PermissionLevel.NORMAL,
    val anonymous: PermissionLevel = PermissionLevel.NORMAL,
): Principal
{
    fun toPermission() = PermissionGroup(read, post, comment, ask, file, delete, anonymous)
}

/**
 * 允许一些权限为空的权限组, 用于修改或查询权限。
 * 为空表示不修改或无权查询
 */
@Serializable
data class PermissionGroupForOperation(
    val id: Long,
    val read: PermissionLevel? = null,
    val post: PermissionLevel? = null,
    val comment: PermissionLevel? = null,
    val ask: PermissionLevel? = null,
    val file: PermissionLevel? = null,
    val delete: PermissionLevel? = null,
    val anonymous: PermissionLevel? = null
)

@Serializable
data class PermissionGroup(
    val read: PermissionLevel = PermissionLevel.NORMAL,
    val post: PermissionLevel = PermissionLevel.NORMAL,
    val comment: PermissionLevel = PermissionLevel.NORMAL,
    val ask: PermissionLevel = PermissionLevel.NORMAL,
    val file: PermissionLevel = PermissionLevel.NORMAL,
    val delete: PermissionLevel = PermissionLevel.NORMAL,
    val anonymous: PermissionLevel = PermissionLevel.NORMAL,
)
{
    /**
     * 检查是否有资格修改某个权限
     * 有资格的条件是：对于某一项权限, 要么不进行修改, 要么修改后的权限不高于自己的权限, 且自己的权限不低于ADMIN
     * @param permission 想要修改的权限
     */
    fun canChange(permission: PermissionGroupForOperation): Boolean =
        (permission.read==null || (read >= permission.read && read >= PermissionLevel.ADMIN)) &&
        (permission.post==null || (post >= permission.post && post >= PermissionLevel.ADMIN)) &&
        (permission.comment==null || (comment >= permission.comment && comment >= PermissionLevel.ADMIN)) &&
        (permission.ask==null || (ask >= permission.ask && ask >= PermissionLevel.ADMIN)) &&
        (permission.file==null || (file >= permission.file && file >= PermissionLevel.ADMIN)) &&
        (permission.delete==null || (delete >= permission.delete && delete >= PermissionLevel.ADMIN)) &&
        (permission.anonymous==null || (anonymous >= permission.anonymous && anonymous >= PermissionLevel.ADMIN))

    fun toChangePermissionOperator() = PermissionGroupForOperation(0L, read, post, comment, ask, file, delete, anonymous)
}

/**
 * 用户数据库交互类
 * 其中邮箱是唯一的, id唯一且自增在创建用户时不可指定。
 * 用户名可以重复, 不能作为登录/注册的唯一标识。
 * 密码单向加密, 加密算法见[JWTAuth.encryptPassword]
 * [UserDatabase]中涉及密码的方法传参均为加密前的密码, 传参前**请不要加密**
 */
object UserDatabase: DataAccessObject<UserDatabase.Users>(Users)
{
    /**
     * 用户信息表
     */
    object Users: IdTable<Long>("users")
    {
        override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
        val username = varchar("username", 100).index()
        val password = text("password")
        val email = varchar("email", 100).uniqueIndex()
        val registrationTime = datetime("registration_time").defaultExpression(CurrentDateTime)
        val figure = text("figure").nullable().default(null)
        val introduction = text("introduction").nullable().default(null)
        val read = enumeration<PermissionLevel>("read").default(PermissionLevel.NORMAL)
        val post = enumeration<PermissionLevel>("post").default(PermissionLevel.NORMAL)
        val comment = enumeration<PermissionLevel>("comment").default(PermissionLevel.NORMAL)
        val ask = enumeration<PermissionLevel>("ask").default(PermissionLevel.NORMAL)
        val file = enumeration<PermissionLevel>("file").default(PermissionLevel.NORMAL)
        val delete = enumeration<PermissionLevel>("delete").default(PermissionLevel.NORMAL)
        val anonymous = enumeration<PermissionLevel>("anonymous").default(PermissionLevel.NORMAL)
        override val primaryKey = PrimaryKey(id)
    }

    /**
     * 创建用户, 若邮箱已存在则返回null
     * @param username 用户名
     * @param password 密码(加密前)
     * @param email 邮箱(唯一)
     * @return 用户ID
     */
    suspend fun createUser(
        username: String,
        password: String,
        email: String,
    ): Long? = query()
    {
        if (select { Users.email eq email }.count() > 0) return@query null // 邮箱已存在
        val psw = JWTAuth.encryptPassword(password) // 加密密码
        insertAndGetId {
            it[Users.username] = username
            it[Users.password] = psw
            it[Users.email] = email
        }.value
    }

    /**
     * 修改用户权限, 为空表示不修改
     * @param id 用户ID
     * @param read 阅读权限
     * @param post 发帖权限
     * @param comment 评论权限
     * @param ask 提问权限
     * @param file 文件权限
     * @param delete 删除权限
     * @param anonymous 匿名权限
     */
    suspend fun changeUserPermission(
        id: Long,
        read: PermissionLevel? = null,
        post: PermissionLevel? = null,
        comment: PermissionLevel? = null,
        ask: PermissionLevel? = null,
        file: PermissionLevel? = null,
        delete: PermissionLevel? = null,
        anonymous: PermissionLevel? = null
    ): Unit = query()
    {
        update({ Users.id eq id })
        {
            if (read != null) it[Users.read] = read
            if (post != null) it[Users.post] = post
            if (comment != null) it[Users.comment] = comment
            if (ask != null) it[Users.ask] = ask
            if (file != null) it[Users.file] = file
            if (delete != null) it[Users.delete] = delete
            if (anonymous != null) it[Users.anonymous] = anonymous
        }
    }

    /**
     * 检查用户登录
     * @param email 邮箱
     * @param password 密码(加密前)
     * @return 用户信息, 若不存在则返回null
     */
    suspend fun checkUserLogin(email: String, password: String): Pair<Boolean,UserFull>? =
        checkUserLoginByEncryptedPassword(email, JWTAuth.encryptPassword(password))
    /**
     * 检查用户登录
     * @param email 邮箱
     * @param password 密码(加密后)
     * @return 若用户不存在返回null,若存在且密码正确第一位为true,否则为false
     */
    suspend fun checkUserLoginByEncryptedPassword(email: String, password: String): Pair<Boolean,UserFull>? = query()
    {
        select { (Users.email eq email) }.singleOrNull()
            ?.let { (it[Users.password]==password) to (deserialize<UserFull>(it)) }
    }

    /**
     * 检查用户登录
     * @param id 用户ID
     * @param password 密码(加密前)
     * @return 用户信息, 若不存在则返回null
     */
    suspend fun checkUserLogin(id: Long, password: String): Pair<Boolean,UserFull>? =
        checkUserLoginByEncryptedPassword(id, JWTAuth.encryptPassword(password))

    /**
     * 检查用户登录
     * @param id 用户ID
     * @param password 密码(加密后)
     * @return 若用户不存在返回null,若存在且密码正确第一位为true,否则为false
     */
    suspend fun checkUserLoginByEncryptedPassword(id: Long, password: String): Pair<Boolean,UserFull>? = query()
    {
        select { (Users.id eq id) }.singleOrNull()
            ?.let { (it[Users.password]==password) to (deserialize<UserFull>(it)) }
    }

    /**
     * 通过ID直接生成JWT Token, 若用户不存在则返回null
     */
    suspend fun makeJwtToken(id: Long): JWTAuth.Token? = query()
    {
        select { Users.id eq id }.singleOrNull()?.let { JWTAuth.makeToken(it[Users.id].value, it[Users.password]) }
    }

    /**
     * 通过邮箱直接生成JWT Token, 若用户不存在则返回null
     */
    suspend fun makeJwtToken(email: String): JWTAuth.Token? = query()
    {
        select { Users.email eq email }.singleOrNull()?.let { JWTAuth.makeToken(it[Users.id].value, it[Users.password]) }
    }

    suspend fun getUser(id: Long): UserFull? = query()
    {
        select { Users.id eq id }.singleOrNull()?.let { deserialize<UserFull>(it) }
    }

    suspend fun getUser(email: String): UserFull? = query()
    {
        select { Users.email eq email }.singleOrNull()?.let { deserialize<UserFull>(it) }
    }

    suspend fun setPassword(email: String, password: String): Boolean = query()
    {
        val psw = JWTAuth.encryptPassword(password) // 加密密码
        update({ Users.email eq email })
        {
            it[Users.password] = psw
        } > 0
    }
}

/**
 * 权限枚举
 */
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
    }
}