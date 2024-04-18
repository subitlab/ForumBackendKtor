package subit.dataClasses

import io.ktor.server.auth.*
import kotlinx.serialization.Serializable

/**
 * 用户数据库数据类
 * @property id 用户ID
 * @property username 用户名
 * @property email 邮箱(唯一)
 * @property registrationTime 注册时间
 * @property introduction 个人简介
 * @property showStars 是否公开收藏
 * @property permission 用户管理权限
 * @property filePermission 文件上传权限
 */
@Serializable
data class UserFull(
    val id: UserId,
    val username: String,
    val email: String,
    val registrationTime: Long,
    val introduction: String?,
    val showStars: Boolean,
    val permission: PermissionLevel,
    val filePermission: PermissionLevel
): Principal
{
    fun toBasicUserInfo() = BasicUserInfo(id, username, registrationTime, introduction, showStars)
}

/**
 * 用户基本信息, 即一般人能看到的信息
 */
@Serializable
data class BasicUserInfo(
    val id: UserId,
    val username: String,
    val registrationTime: Long,
    val introduction: String?,
    val showStars: Boolean
)