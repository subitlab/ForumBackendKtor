package subit.database

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import subit.ForumBackend
import subit.database.UserFull.Companion.set
import java.util.*
import javax.naming.OperationNotSupportedException

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
@Serializable
data class UserFull(
    val id: Long,
    val username: String,
    val password: String,
    val email: String = "",
    val phone: String = "",
    val registrationTime: Long = System.currentTimeMillis(),
    val introduction: String = "",
    val read: Permission = Permission.NORMAL,
    val post: Permission = Permission.NORMAL,
    val comment: Permission = Permission.NORMAL,
    val ask: Permission = Permission.NORMAL,
    val file: Permission = Permission.NORMAL,
    val delete: Permission = Permission.NORMAL,
    val anonymous: Permission = Permission.NORMAL,
)
{
    constructor(row: ResultRow): this(
        row[UserDatabase.Users.id],
        row[UserDatabase.Users.username],
        row[UserDatabase.Users.password],
        row[UserDatabase.Users.email],
        row[UserDatabase.Users.phone],
        row[UserDatabase.Users.registrationTime],
        row[UserDatabase.Users.introduction],
        row[UserDatabase.Users.read],
        row[UserDatabase.Users.post],
        row[UserDatabase.Users.comment],
        row[UserDatabase.Users.ask],
        row[UserDatabase.Users.file],
        row[UserDatabase.Users.delete],
        row[UserDatabase.Users.anonymous],
    )

    /**
     * 将注册时间转换为Date
     */
    fun registrationTimeAsDate() = Date(registrationTime)

    companion object
    {
        fun UpdateBuilder<Int>.set(user: UserFull)
        {
            this[UserDatabase.Users.id] = user.id
            this[UserDatabase.Users.username] = user.username
            this[UserDatabase.Users.password] = user.password
            this[UserDatabase.Users.email] = user.email
            this[UserDatabase.Users.phone] = user.phone
            this[UserDatabase.Users.registrationTime] = user.registrationTime
            this[UserDatabase.Users.introduction] = user.introduction
            this[UserDatabase.Users.read] = user.read
            this[UserDatabase.Users.post] = user.post
            this[UserDatabase.Users.comment] = user.comment
            this[UserDatabase.Users.ask] = user.ask
            this[UserDatabase.Users.file] = user.file
            this[UserDatabase.Users.delete] = user.delete
            this[UserDatabase.Users.anonymous] = user.anonymous
        }
    }
}

/**
 * 数据库交互类
 */
object UserDatabase
{
    /**
     * 用户信息表
     */
    object Users: Table()
    {
        val id = long("id").uniqueIndex()
        val username = text("name")
        val password = text("password")
        val email = text("email").default("")
        val phone = text("phone").default("")
        val registrationTime = long("registration_time")
        val introduction = text("introduction").default("")
        val read = enumeration<Permission>("read").default(Permission.NORMAL)
        val post = enumeration<Permission>("post").default(Permission.NORMAL)
        val comment = enumeration<Permission>("comment").default(Permission.NORMAL)
        val ask = enumeration<Permission>("ask").default(Permission.NORMAL)
        val file = enumeration<Permission>("file").default(Permission.NORMAL)
        val delete = enumeration<Permission>("delete").default(Permission.NORMAL)
        val anonymous = enumeration<Permission>("anonymous").default(Permission.NORMAL)
    }

    init
    {
        transaction(ForumBackend.database) { SchemaUtils.create(Users) }
    }

    /**
     * 执行数据库操作
     */
    suspend inline fun <T> dbQuery(crossinline block: suspend ()->T) =
        newSuspendedTransaction(Dispatchers.IO) { return@newSuspendedTransaction block() }

    /**
     * 查找用户, null为不限制
     * [T]必须为[UserFull], [List]<[UserFull]>或者[Array]<[UserFull]>
     */
    suspend inline fun <reified T> finedUser(
        id: Long? = null,
        username: String? = null,
        password: String? = null,
        email: String? = null,
        phone: String? = null,
        registrationTime: Long? = null,
        introduction: String? = null,
        read: Permission? = null,
        post: Permission? = null,
        comment: Permission? = null,
        ask: Permission? = null,
        file: Permission? = null,
        delete: Permission? = null,
        anonymous: Permission? = null,
    ): T?
    {
        val q = dbQuery()
        {
            Users.select()
            {
                AndHelper.create()
                    .and(Users.id, id)
                    .and(Users.username, username)
                    .and(Users.password, password)
                    .and(Users.email, email)
                    .and(Users.phone, phone)
                    .and(Users.registrationTime, registrationTime)
                    .and(Users.introduction, introduction)
                    .and(Users.read, read)
                    .and(Users.post, post)
                    .and(Users.comment, comment)
                    .and(Users.ask, ask)
                    .and(Users.file, file)
                    .and(Users.delete, delete)
                    .and(Users.anonymous, anonymous)
                    .op
            }
        }
        return when (T::class)
        {
            UserFull::class        -> q.singleOrNull()?.let { UserFull(it) } as T?
            List::class            -> q.map { UserFull(it) } as T?
            Array<UserFull>::class -> q.map { UserFull(it) }.toTypedArray() as T?
            Boolean::class         -> (q.singleOrNull()!=null) as T?
            else                   -> throw OperationNotSupportedException("Unsupported type: ${T::class}")
        }
    }

    /**
     * 创建用户
     */
    suspend fun createUser(user: UserFull) = dbQuery { Users.insert { it.set(user) } }

    /**
     * 删除用户
     */
    suspend fun updateUser(user: UserFull) = dbQuery { Users.update({ Users.id eq user.id }) { it.set(user) } }

    /**
     * 帮助创建And条件
     */
    class AndHelper
    {
        companion object
        {
            @JvmStatic
            fun create() = AndHelper()
        }

        var op: Op<Boolean> = Op.TRUE
        fun <T> and(column: Column<T>, value: T?): AndHelper
        {
            if (value!=null) op = op.and(column eq value)
            return this
        }
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