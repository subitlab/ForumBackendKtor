package subit.dataClasses

sealed interface Notice
{
    enum class Type
    {
        /**
         * 系统通知
         */
        SYSTEM,

        /**
         * 帖子被评论
         */
        POST_COMMENT,

        /**
         * 评论被回复
         */
        COMMENT_REPLY,

        /**
         * 点赞
         */
        LIKE,

        /**
         * 收藏
         */
        STAR,
    }

    companion object
    {
        fun makeSystemNotice(id: Long = 0, user: UserId, content: String): Notice = SystemNotice(id, user, content)
        fun makeObjectMessage(id: Long = 0, user: UserId, type: Type, obj: Long, count: Long = 0): ObjectNotice = when (type)
        {
            Type.POST_COMMENT -> PostCommentNotice(id, user, obj, count)
            Type.COMMENT_REPLY -> CommentReplyNotice(id, user, obj, count)
            Type.LIKE -> LikeNotice(id, user, obj, count)
            Type.STAR -> StarNotice(id, user, obj, count)
            else -> throw IllegalArgumentException("Invalid type: $type")
        }
    }

    val id: NoticeId
    val type: Type
    val user: UserId

    interface ObjectNotice: Notice
    {
        val obj: Long
        val count: Long
    }

    /**
     * 系统通知
     * @property content 内容
     */
    data class SystemNotice(
        override val id: NoticeId,
        override val user: UserId,
        val content: String,
    ): Notice
    {
        override val type: Type get() = Type.SYSTEM
    }

    /**
     * 评论通知, 即有人评论了用户的帖子. 对于同一个帖子的多个评论, 累加[count]
     * @property post 帖子
     * @property count 这个帖子的评论数量
     */
    data class PostCommentNotice(
        override val id: NoticeId,
        override val user: UserId,
        val post: PostId,
        override val count: Long
    ): ObjectNotice
    {
        override val type: Type get() = Type.POST_COMMENT
        override val obj: Long get() = post
    }

    /**
     * 评论回复通知, 即有人回复了用户的评论. 对于同一个评论的多个回复, 累加[count]
     */
    data class CommentReplyNotice(
        override val id: NoticeId,
        override val user: UserId,
        val comment: CommentId,
        override val count: Long
    ): ObjectNotice
    {
        override val type: Type get() = Type.COMMENT_REPLY
        override val obj: Long get() = comment
    }

    /**
     * 点赞通知, 即有人点赞了用户的帖子. 对于同一个帖子的多个点赞, 累加[count]
     * @property post 帖子
     * @property count 这个帖子的点赞数量
     */
    data class LikeNotice(
        override val id: NoticeId,
        override val user: UserId,
        val post: PostId,
        override val count: Long
    ): ObjectNotice
    {
        override val type: Type get() = Type.LIKE
        override val obj: Long get() = post
    }

    /**
     * 收藏通知, 即有人收藏了用户的帖子. 对于同一个帖子的多个收藏, 累加[count]
     * @property post 帖子
     * @property count 这个帖子的收藏数量
     */
    data class StarNotice(
        override val id: NoticeId,
        override val user: UserId,
        val post: PostId,
        override val count: Long
    ): ObjectNotice
    {
        override val type: Type get() = Type.STAR
        override val obj: Long get() = post
    }
}