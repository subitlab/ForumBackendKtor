package subit.database

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import subit.JWTAuth
import subit.dataClasses.PermissionLevel
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserFull
import subit.dataClasses.UserId

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
    object Users: IdTable<Int>("users")
    {
        override val id: Column<EntityID<UserId>> = integer("id").autoIncrement().entityId()
        val username = varchar("username", 100).index()
        val password = text("password")
        val email = varchar("email", 100).uniqueIndex()
        val registrationTime = timestamp("registration_time").defaultExpression(CurrentTimestamp())
        val introduction = text("introduction").nullable().default(null)
        val showStars = bool("show_stars").default(true)
        val permission = enumeration<PermissionLevel>("permission").default(PermissionLevel.NORMAL)
        val filePermission = enumeration<PermissionLevel>("file_permission").default(PermissionLevel.NORMAL)
        override val primaryKey = PrimaryKey(id)
    }

    private fun deserialize(row: ResultRow) = UserFull(
        id = row[Users.id].value,
        username = row[Users.username],
        email = row[Users.email],
        registrationTime = row[Users.registrationTime].toEpochMilli(),
        introduction = row[Users.introduction] ?: "",
        showStars = row[Users.showStars],
        permission = row[Users.permission],
        filePermission = row[Users.filePermission]
    )

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
    ): UserId? = query()
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
     * 检查用户登录
     * @param email 邮箱
     * @param password 密码(加密前)
     * @return 用户信息, 若不存在则返回null
     */
    suspend fun checkUserLogin(email: String, password: String): Pair<Boolean, UserFull>? =
        checkUserLoginByEncryptedPassword(email, JWTAuth.encryptPassword(password))

    /**
     * 检查用户登录
     * @param email 邮箱
     * @param password 密码(加密后)
     * @return 若用户不存在返回null,若存在且密码正确第一位为true,否则为false
     */
    private suspend fun checkUserLoginByEncryptedPassword(email: String, password: String): Pair<Boolean, UserFull>? = query()
    {
        select { (Users.email eq email) }.singleOrNull()
            ?.let { (it[Users.password] == password) to (deserialize(it)) }
    }

    /**
     * 检查用户登录
     * @param id 用户ID
     * @param password 密码(加密前)
     * @return 用户信息, 若不存在则返回null
     */
    suspend fun checkUserLogin(id: UserId, password: String): Pair<Boolean, UserFull>? =
        checkUserLoginByEncryptedPassword(id, JWTAuth.encryptPassword(password))

    /**
     * 检查用户登录
     * @param id 用户ID
     * @param password 密码(加密后)
     * @return 若用户不存在返回null,若存在且密码正确第一位为true,否则为false
     */
    suspend fun checkUserLoginByEncryptedPassword(id: UserId, password: String): Pair<Boolean, UserFull>? = query()
    {
        select { (Users.id eq id) }.singleOrNull()
            ?.let { (it[Users.password] == password) to (deserialize(it)) }
    }

    /**
     * 通过ID直接生成JWT Token, 若用户不存在则返回null
     */
    suspend fun makeJwtToken(id: UserId): JWTAuth.Token? = query()
    {
        select { Users.id eq id }.singleOrNull()?.let { JWTAuth.makeToken(it[Users.id].value, it[Users.password]) }
    }

    /**
     * 通过邮箱直接生成JWT Token, 若用户不存在则返回null
     */
    suspend fun makeJwtToken(email: String): JWTAuth.Token? = query()
    {
        select { Users.email eq email }.singleOrNull()?.let { JWTAuth.makeToken(it[id].value, it[Users.password]) }
    }

    suspend fun getUser(id: UserId): UserFull? = query()
    {
        select { Users.id eq id }.singleOrNull()?.let(::deserialize)
    }

    suspend fun getUser(email: String): UserFull? = query()
    {
        select { Users.email eq email }.singleOrNull()?.let(::deserialize)
    }

    suspend fun setPassword(email: String, password: String): Boolean = query()
    {
        val psw = JWTAuth.encryptPassword(password) // 加密密码
        update({ Users.email eq email }) { it[Users.password] = psw } > 0
    }

    suspend fun changeUsername(id: UserId, username: String): Boolean = query()
    {
        update({ Users.id eq id }) { it[Users.username] = username } > 0
    }

    suspend fun changeIntroduction(id: UserId, introduction: String): Boolean = query()
    {
        update({ Users.id eq id }) { it[Users.introduction] = introduction } > 0
    }

    suspend fun changeShowStars(id: UserId, showStars: Boolean): Boolean = query()
    {
        update({ Users.id eq id }) { it[Users.showStars] = showStars } > 0
    }

    suspend fun changePermission(id: UserId, permission: PermissionLevel): Boolean = query()
    {
        update({ Users.id eq id }) { it[Users.permission] = permission } > 0
    }

    suspend fun changeFilePermission(id: UserId, permission: PermissionLevel): Boolean = query()
    {
        update({ Users.id eq id }) { it[Users.filePermission] = permission } > 0
    }

    suspend fun searchUser(username: String, begin: Long, count: Int): Slice<UserFull> = query()
    {
        Users.select { Users.username like "%$username%" }.asSlice(begin, count).map(::deserialize)
    }
}