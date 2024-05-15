package subit.database.memoryImpl

import subit.JWTAuth
import subit.dataClasses.PermissionLevel
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserFull
import subit.dataClasses.UserId
import subit.dataClasses.UserId.Companion.toUserId
import subit.database.Users
import java.util.*

class UsersImpl: Users
{
    private val map = Collections.synchronizedMap(hashMapOf<UserId, UserFull>())
    private val emailMap = Collections.synchronizedMap(hashMapOf<String, UserId>())
    private val passwords = Collections.synchronizedMap(hashMapOf<UserId, String>())
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

    override suspend fun getEncryptedPassword(id: UserId): String? = passwords[id]
    override suspend fun getEncryptedPassword(email: String): String? = emailMap[email]?.let { passwords[it] }

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