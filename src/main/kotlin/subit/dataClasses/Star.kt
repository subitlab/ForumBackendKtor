package subit.dataClasses

import kotlinx.serialization.Serializable

/**
 * 收藏信息
 * @property user 收藏用户
 * @property post 收藏帖子
 * @property time 收藏时间
 */
@Serializable
data class Star(
    val user: UserId,
    val post: Long?,
    val time: Long
)