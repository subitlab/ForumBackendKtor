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
         * 私聊消息
         */
        PRIVATE_CHAT,

        /**
         * 评论
         */
        COMMENT,

        /**
         * 点赞
         */
        LIKE,

        /**
         * 收藏
         */
        STAR,

        /**
         * 待处理的举报(管理员)
         */
        REPORT,
    }

    val id: NoticeId
    val type: Type
    val user: UserId

    interface CountNotice: Notice
    {
        val count: Long
    }

    interface PostNotice: CountNotice
    {
        val post: PostId
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
     * 私信通知, 仅记录有多少条未读私信
     * @property count 未读私信数量
     */
    data class PrivateChatNotice(
        override val id: NoticeId,
        override val user: UserId,
        override val count: Long
    ): CountNotice
    {
        override val type: Type get() = Type.PRIVATE_CHAT
    }

    /**
     * 评论通知, 即有人评论了用户的帖子. 对于同一个帖子的多个评论, 累加[count]
     * @property post 帖子
     * @property count 这个帖子的评论数量
     */
    data class CommentNotice(
        override val id: NoticeId,
        override val user: UserId,
        override val post: PostId,
        override val count: Long
    ): PostNotice
    {
        override val type: Type get() = Type.COMMENT
    }

    /**
     * 点赞通知, 即有人点赞了用户的帖子. 对于同一个帖子的多个点赞, 累加[count]
     * @property post 帖子
     * @property count 这个帖子的点赞数量
     */
    data class LikeNotice(
        override val id: NoticeId,
        override val user: UserId,
        override val post: PostId,
        override val count: Long
    ): PostNotice
    {
        override val type: Type get() = Type.LIKE
    }

    /**
     * 收藏通知, 即有人收藏了用户的帖子. 对于同一个帖子的多个收藏, 累加[count]
     * @property post 帖子
     * @property count 这个帖子的收藏数量
     */
    data class StarNotice(
        override val id: NoticeId,
        override val user: UserId,
        override val post: PostId,
        override val count: Long
    ): PostNotice
    {
        override val type: Type get() = Type.STAR
    }

    /**
     * 待处理的举报通知, 该通知只有管理员才会收到
     * @property count 有多少个待处理的举报
     */
    data class ReportNotice(
        override val id: NoticeId,
        override val user: UserId,
        override val count: Long
    ): CountNotice
    {
        override val type: Type get() = Type.REPORT
    }
}