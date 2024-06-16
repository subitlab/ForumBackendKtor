@file:Suppress("PackageDirectoryMismatch")

package subit.router.notice

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.dataClasses.Notice.*
import subit.dataClasses.NoticeId.Companion.toNoticeIdOrNull
import subit.database.Notices
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.respond
import subit.utils.statuses

fun Route.notice()
{
    route("/notice", {
        tags = listOf("通知")
        request {
            authenticated(true)
        }
    })
    {
        get("/list", {
            description = """
                获取通知列表
                
                除去此接口获取的通知外, 待处理的举报和未读的私信也应在通知中显示, 
                详细请参阅 获取举报列表接口(/report/list) 和 获取所有未读私信数量接口(/privateChat/unread/all)
                """.trimIndent()
            request {
                paged()
                queryParameter<Type>("type")
                {
                    required = false
                    description = "通知类型, 可选值为${Type.entries.joinToString { it.name }}, 不填则获取所有通知"
                    example = Type.SYSTEM
                }
            }
            response {
                statuses<Slice<NoticeId>>(HttpStatus.OK, example = sliceOf(NoticeId(0)))
                statuses(HttpStatus.Unauthorized)
            }
        }) { getList() }

        get("/{id}", {
            description = """
                获取通知, 通知有多种类型, 每种类型有结构不同, 可通过type区分. 请注意处理.
                
                相应中的type字段为通知类型, 可能为${Type.entries.joinToString { it.name }}
                
                - obj: 对象, 当类型为点赞/收藏/评论/回复时, 为帖子ID/评论ID, 其他情况下为null
                - count: 数量, 当类型为点赞/收藏/评论/回复/待处理举报时, 为数量, 其他情况下为null
                - content: 内容, 当类型为系统通知时, 为通知内容, 其他情况下为null
                """.trimIndent()
            request {
                pathParameter<RawNoticeId>("id")
                {
                    required = true
                    description = "通知ID"
                }
            }
            response {
                statuses<NoticeResponse>(
                    HttpStatus.OK, examples = listOf(
                        NoticeResponse.fromNotice(StarNotice.example),
                        NoticeResponse.fromNotice(LikeNotice.example),
                        NoticeResponse.fromNotice(SystemNotice.example),
                        NoticeResponse.fromNotice(PostCommentNotice.example),
                        NoticeResponse.fromNotice(CommentReplyNotice.example),
                    )
                )
                statuses(HttpStatus.Unauthorized, HttpStatus.NotFound, HttpStatus.BadRequest)
            }
        }) { getNotice() }

        delete("/{id}", {
            description = "删除通知(设为已读)"
            request {
                pathParameter<RawNoticeId>("id")
                {
                    required = true
                    description = "通知ID"
                    example = NoticeId(0)
                }
            }
            response {
                statuses(HttpStatus.OK, HttpStatus.Unauthorized, HttpStatus.NotFound, HttpStatus.BadRequest)
            }
        }) { deleteNotice() }

        delete("/all", {
            description = "删除所有通知(设为已读)"
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
            }
        }) { deleteAll() }
    }
}

private suspend fun Context.getList()
{
    val (begin, count) = call.getPage()
    val type = call.parameters["type"]?.runCatching { Type.valueOf(this) }?.getOrNull()
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val notices = get<Notices>()
    notices.getNotices(loginUser.id, type, begin, count).map(Notice::id).let { call.respond(HttpStatus.OK, it) }
}

@Serializable
private data class NoticeResponse(
    val id: NoticeId,
    val user: UserId,
    val type: Type,
    val obj: Long?,
    val count: Long?,
    val content: String?
)
{
    companion object
    {
        fun fromNotice(notice: Notice): NoticeResponse
        {
            val (obj, count) = (notice as? ObjectNotice).let { it?.obj?.value to it?.count }
            val content = (notice as? SystemNotice)?.content
            val response = NoticeResponse(
                id = notice.id,
                user = notice.user,
                type = notice.type,
                obj = obj?.toLong(),
                count = count,
                content = content
            )
            return response
        }
    }
}

private suspend fun Context.getNotice()
{
    val id = call.parameters["id"]?.toNoticeIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val notices = get<Notices>()
    val user = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val notice = notices.getNotice(id)?.takeIf { it.user == user.id } ?: return call.respond(HttpStatus.NotFound)
    /*
     * 注意由于[Notice.type]不在构造函数中等问题, 无法序列化, 故手动转为[NoticeResponse]
     */

    call.respond(HttpStatus.OK, NoticeResponse.fromNotice(notice))
}

private suspend fun Context.deleteNotice()
{
    val id = call.parameters["id"]?.toNoticeIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val notices = get<Notices>()
    val user = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    notices.getNotice(id)?.takeIf { it.user == user.id }?.let { notices.deleteNotice(id) }
    call.respond(HttpStatus.OK)
}

private suspend fun Context.deleteAll()
{
    val user = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val notices = get<Notices>()
    notices.deleteNotices(user.id)
    call.respond(HttpStatus.OK)
}