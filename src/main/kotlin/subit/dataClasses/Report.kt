package subit.dataClasses

data class Report(val id: ReportId, val objectType: ReportObject, val objectId: Long, val user: UserId, val reason: String)
{
    val oUser: UserId? get() = if (objectType == ReportObject.USER) objectId.toUserId() else null
    val oPost: PostId? get() = if (objectType == ReportObject.POST) objectId.toPostId() else null
    val oComment: CommentId? get() = if (objectType == ReportObject.COMMENT) objectId.toCommentId() else null
    val oBlock: BlockId? get() = if (objectType == ReportObject.BLOCK) objectId.toBlockId() else null
}

enum class ReportObject
{
    POST, COMMENT, USER, BLOCK
}