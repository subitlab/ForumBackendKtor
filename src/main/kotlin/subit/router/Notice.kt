@file:Suppress("PackageDirectoryMismatch")

package subit.router.notice

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.dataClasses.Notice.*
import subit.database.Notices
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.respond
import subit.utils.statuses

fun Route.notice()
{
    route("/notice", {
        tags = listOf("通知")
    })
    {
        get("/list", {
            description = """
                获取通知列表
                
                除去此接口获取的通知外, 待处理的举报和未读的私信也应在通知中显示, 
                详细请参阅 获取举报列表接口(/report/list) 和 获取所有未读私信数量接口(/privateChat/unread/all)
                """.trimIndent()
            request {
                authenticated(true)
                paged()
                queryParameter<Type>("type")
                {
                    required = false
                    description = "通知类型, 可选值为${Type.entries.joinToString { it.name }}, 不填则获取所有通知"
                }
            }
            response {
                statuses<Slice<NoticeId>>(HttpStatus.OK)
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
                authenticated(true)
                pathParameter<NoticeId>("id") { required = true; description = "通知ID" }
            }
            response {
                statuses<NoticeResponse>(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
                statuses(HttpStatus.NotFound)
                statuses(HttpStatus.BadRequest)
            }
        }) { getNotice() }

        delete("/{id}", {
            description = "删除通知(设为已读)"
            request {
                authenticated(true)
                pathParameter<NoticeId>("id") { required = true; description = "通知ID" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
                statuses(HttpStatus.NotFound)
                statuses(HttpStatus.BadRequest)
            }
        }) { deleteNotice() }

        delete("/all", {
            description = "删除所有通知(设为已读)"
            request {
                authenticated(true)
            }
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
    notices.getNotices(loginUser.id, type, begin, count).map(Notice::id).let { call.respond(it) }
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

private suspend fun Context.getNotice()
{
    val id = call.parameters["id"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val notices = get<Notices>()
    val user = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val notice = notices.getNotice(id)?.takeIf { it.user == user.id } ?: return call.respond(HttpStatus.NotFound)
    /*
     * 注意由于[Notice.type]不在构造函数中等问题, 无法序列化, 故手动转为[NoticeResponse]
     */
    val (obj, count) = (notice as? ObjectNotice).let { it?.obj to it?.count }
    val content = (notice as? SystemNotice)?.content
    val response = NoticeResponse(
        id = notice.id,
        user = notice.user,
        type = notice.type,
        obj = obj,
        count = count,
        content = content
    )
    call.respond(response)
}

private suspend fun Context.deleteNotice()
{
    val id = call.parameters["id"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
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