package subit.database

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import subit.ForumBackend
import java.util.*
import javax.naming.OperationNotSupportedException

@Serializable
data class UserFull(
    val id: Long,
    val username: String,
    val password: String,
    val email: String,
    val phone: String,
    val registrationTime: Long,
    val introduction: String,
    val read: Permission,
    val post: Permission,
    val comment: Permission,
    val ask: Permission,
    val file: Permission,
    val delete: Permission,
    val anonymous: Permission,
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

    fun registrationTimeAsDate() = Date(registrationTime)
}

object UserDatabase
{
    object Users: Table()
    {
        val id = long("id").autoIncrement().uniqueIndex()
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

    suspend inline fun <T> dbQuery(crossinline block: suspend ()->T) =
        newSuspendedTransaction(Dispatchers.IO) { return@newSuspendedTransaction block() }

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