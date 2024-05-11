package subit.dataClasses

import kotlinx.serialization.Serializable

/**
 * 帖子/板块/评论的状态
 */
@Serializable
enum class State
{
    /**
     * 正常
     */
    NORMAL,

    /**
     * 被删除
     */
    DELETED;
}