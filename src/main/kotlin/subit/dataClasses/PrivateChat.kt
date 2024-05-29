package subit.dataClasses

import kotlinx.serialization.Serializable

/**
 * 私信信息
 * @property from 发送者ID
 * @property to 接收者ID
 * @property time 发送时间
 * @property content 内容
 */
@Serializable
data class PrivateChat(
    val from: UserId,
    val to: UserId,
    val time: Long,
    val content: String,
)
{
    companion object
    {
        val example = PrivateChat(
            UserId(1),
            UserId(2),
            System.currentTimeMillis(),
            "私信内容"
        )
    }
}