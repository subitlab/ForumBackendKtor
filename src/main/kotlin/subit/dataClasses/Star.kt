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
    val post: PostId?,
    val time: Long
)
{
    companion object
    {
        val example = Star(
            UserId(1),
            PostId(1),
            System.currentTimeMillis()
        )
    }
}