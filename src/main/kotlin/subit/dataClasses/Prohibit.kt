package subit.dataClasses

import kotlinx.serialization.Serializable

/**
 * 封禁信息
 * @property user 被封禁用户ID
 * @property time 封禁时间
 * @property reason 封禁原因
 * @property operator 操作者ID
 */
@Serializable
data class Prohibit(
    val user: UserId,
    val time: Long,
    val reason: String,
    val operator: UserId,
)