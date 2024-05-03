package subit.database.sqlImpl

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
import subit.database.Users

class UsersImpl: DaoSqlImpl<UsersImpl.UserTable>(UserTable), Users
{
    /**
     * 用户信息表
     */
    object UserTable: IdTable<Int>("users")
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
        id = row[UserTable.id].value,
        username = row[UserTable.username],
        email = row[UserTable.email],
        registrationTime = row[UserTable.registrationTime].toEpochMilli(),
        introduction = row[UserTable.introduction] ?: "",
        showStars = row[UserTable.showStars],
        permission = row[UserTable.permission],
        filePermission = row[UserTable.filePermission]
    )

    override suspend fun createUser(
        username: String,
        password: String,
        email: String,
    ): UserId? = query()
    {
        if (select { UserTable.email eq email }.count() > 0) return@query null // 邮箱已存在
        val psw = JWTAuth.encryptPassword(password) // 加密密码
        insertAndGetId {
            it[UserTable.username] = username
            it[UserTable.password] = psw
            it[UserTable.email] = email
        }.value
    }

    override suspend fun checkUserLoginByEncryptedPassword(email: String, password: String): Pair<Boolean, UserFull>? = query()
    {
        select { (UserTable.email eq email) }.singleOrNull()
            ?.let { (it[UserTable.password] == password) to (deserialize(it)) }
    }

    override suspend fun checkUserLoginByEncryptedPassword(id: UserId, password: String): Pair<Boolean, UserFull>? = query()
    {
        select { (UserTable.id eq id) }.singleOrNull()
            ?.let { (it[UserTable.password] == password) to (deserialize(it)) }
    }

    override suspend fun makeJwtToken(id: UserId): JWTAuth.Token? = query()
    {
        select { UserTable.id eq id }.singleOrNull()
            ?.let { JWTAuth.makeTokenByEncryptPassword(it[UserTable.id].value, it[password]) }
    }

    override suspend fun makeJwtToken(email: String): JWTAuth.Token? = query()
    {
        select { UserTable.email eq email }.singleOrNull()
            ?.let { JWTAuth.makeTokenByEncryptPassword(it[id].value, it[password]) }
    }

    override suspend fun getUser(id: UserId): UserFull? = query()
    {
        select { UserTable.id eq id }.singleOrNull()?.let(::deserialize)
    }

    override suspend fun getUser(email: String): UserFull? = query()
    {
        select { UserTable.email eq email }.singleOrNull()?.let(::deserialize)
    }

    override suspend fun setPassword(email: String, password: String): Boolean = query()
    {
        val psw = JWTAuth.encryptPassword(password) // 加密密码
        update({ UserTable.email eq email }) { it[UserTable.password] = psw } > 0
    }

    override suspend fun changeUsername(id: UserId, username: String): Boolean = query()
    {
        update({ UserTable.id eq id }) { it[UserTable.username] = username } > 0
    }

    override suspend fun changeIntroduction(id: UserId, introduction: String): Boolean = query()
    {
        update({ UserTable.id eq id }) { it[UserTable.introduction] = introduction } > 0
    }

    override suspend fun changeShowStars(id: UserId, showStars: Boolean): Boolean = query()
    {
        update({ UserTable.id eq id }) { it[UserTable.showStars] = showStars } > 0
    }

    override suspend fun changePermission(id: UserId, permission: PermissionLevel): Boolean = query()
    {
        update({ UserTable.id eq id }) { it[UserTable.permission] = permission } > 0
    }

    override suspend fun changeFilePermission(id: UserId, permission: PermissionLevel): Boolean = query()
    {
        update({ UserTable.id eq id }) { it[filePermission] = permission } > 0
    }

    override suspend fun searchUser(username: String, begin: Long, count: Int): Slice<UserFull> = query()
    {
        UserTable.select { UserTable.username like "%$username%" }.asSlice(begin, count).map(::deserialize)
    }
}