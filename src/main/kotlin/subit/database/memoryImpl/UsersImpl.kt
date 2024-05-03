package subit.database.memoryImpl

import subit.JWTAuth
import subit.dataClasses.*
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.Users
import java.util.Collections

class UsersImpl: Users
{
    private val map = Collections.synchronizedMap(mutableMapOf<UserId, UserFull>())
    private val emailMap = Collections.synchronizedMap(mutableMapOf<String, UserId>())
    private val passwords = Collections.synchronizedMap(mutableMapOf<UserId, String>())
    override suspend fun createUser(username: String, password: String, email: String): UserId?
    {
        if (emailMap.containsKey(email)) return null
        val id = (emailMap.size+1).toUserId()
        emailMap[email] = id
        map[id] = UserFull(
            id = id,
            username = username,
            email = email,
            introduction = null,
            showStars = true,
            permission = PermissionLevel.NORMAL,
            filePermission = PermissionLevel.NORMAL,
            registrationTime = System.currentTimeMillis()
        )
        passwords[id] = JWTAuth.encryptPassword(password)
        return id
    }

    override suspend fun checkUserLoginByEncryptedPassword(email: String, password: String): Pair<Boolean, UserFull>?
    {
        val id = emailMap[email] ?: return null
        return checkUserLoginByEncryptedPassword(id, password)
    }

    override suspend fun checkUserLoginByEncryptedPassword(id: UserId, password: String): Pair<Boolean, UserFull>?
    {
        val user = map[id] ?: return null
        return (passwords[id] == password) to user
    }

    override suspend fun makeJwtToken(id: UserId): JWTAuth.Token?
    {
        val password = passwords[id] ?: return null
        return JWTAuth.makeTokenByEncryptPassword(id, password)
    }

    override suspend fun makeJwtToken(email: String): JWTAuth.Token?
    {
        val id = emailMap[email] ?: return null
        return makeJwtToken(id)
    }

    override suspend fun getUser(id: UserId): UserFull? = map[id]
    override suspend fun getUser(email: String): UserFull? = emailMap[email]?.let { map[it] }
    override suspend fun setPassword(email: String, password: String): Boolean =
        emailMap[email]?.let { id ->
            passwords[id] = JWTAuth.encryptPassword(password)
            true
        } ?: false

    override suspend fun changeUsername(id: UserId, username: String): Boolean =
        map[id]?.let {
            map[id] = it.copy(username = username)
            true
        } ?: false

    override suspend fun changeIntroduction(id: UserId, introduction: String): Boolean =
        map[id]?.let {
            map[id] = it.copy(introduction = introduction)
            true
        } ?: false

    override suspend fun changeShowStars(id: UserId, showStars: Boolean): Boolean =
        map[id]?.let {
            map[id] = it.copy(showStars = showStars)
            true
        } ?: false

    override suspend fun changePermission(id: UserId, permission: PermissionLevel): Boolean =
        map[id]?.let {
            map[id] = it.copy(permission = permission)
            true
        } ?: false

    override suspend fun changeFilePermission(id: UserId, permission: PermissionLevel): Boolean =
        map[id]?.let {
            map[id] = it.copy(filePermission = permission)
            true
        } ?: false

    override suspend fun searchUser(username: String, begin: Long, count: Int): Slice<UserFull> =
        map.values.filter { it.username.contains(username) }.asSlice(begin, count)
}