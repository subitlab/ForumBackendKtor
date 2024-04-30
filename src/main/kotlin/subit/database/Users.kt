package subit.database

import subit.JWTAuth
import subit.dataClasses.PermissionLevel
import subit.dataClasses.Slice
import subit.dataClasses.UserFull
import subit.dataClasses.UserId
import subit.database.sqlImpl.UsersImpl

/**
 * 用户数据库交互类
 * 其中邮箱是唯一的, id唯一且自增在创建用户时不可指定。
 * 用户名可以重复, 不能作为登录/注册的唯一标识。
 * 密码单向加密, 加密算法见[JWTAuth.encryptPassword]
 * [UsersImpl]中涉及密码的方法传参均为加密前的密码, 传参前**请不要加密**
 */
interface Users
{
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
    ): UserId?

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
    suspend fun checkUserLoginByEncryptedPassword(email: String, password: String): Pair<Boolean, UserFull>?

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
    suspend fun checkUserLoginByEncryptedPassword(id: UserId, password: String): Pair<Boolean, UserFull>?

    /**
     * 通过ID直接生成JWT Token, 若用户不存在则返回null
     */
    suspend fun makeJwtToken(id: UserId): JWTAuth.Token?

    /**
     * 通过邮箱直接生成JWT Token, 若用户不存在则返回null
     */
    suspend fun makeJwtToken(email: String): JWTAuth.Token?
    suspend fun getUser(id: UserId): UserFull?
    suspend fun getUser(email: String): UserFull?

    /**
     * 重设密码, 若用户不存在返回false
     */
    suspend fun setPassword(email: String, password: String): Boolean

    /**
     * 若用户不存在返回false
     */
    suspend fun changeUsername(id: UserId, username: String): Boolean

    /**
     * 若用户不存在返回false
     */
    suspend fun changeIntroduction(id: UserId, introduction: String): Boolean

    /**
     * 若用户不存在返回false
     */
    suspend fun changeShowStars(id: UserId, showStars: Boolean): Boolean

    /**
     * 若用户不存在返回false
     */
    suspend fun changePermission(id: UserId, permission: PermissionLevel): Boolean

    /**
     * 若用户不存在返回false
     */
    suspend fun changeFilePermission(id: UserId, permission: PermissionLevel): Boolean

    /**
     * 若用户不存在返回false
     */
    suspend fun searchUser(username: String, begin: Long, count: Int): Slice<UserFull>
}