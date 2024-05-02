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
            description = "获取通知列表"
            request {
                authenticated(true)
                paged()
            }
            response {
                statuses<Slice<NoticeId>>(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
            }
        }) { getList() }

        get("/{id}", {
            description = """
                获取通知, 通知有多种类型, 每种类型有结构不同, 可通过type区分. 请注意处理.
                
                相应中的type字段为通知类型, 可能为SYSTEM, COMMENT, LIKE, STAR, PRIVATE_CHAT, REPORT
                
                - 当type为SYSTEM时, content字段为通知内容, count/post为null, 其余类型content字段为null
                - 当type为COMMENT, LIKE, STAR时, post字段为帖子ID, count字段为数量即这一帖子被评论/点赞/收藏的数量, 否则post字段为null
                - 当type为PRIVATE_CHAT时, count字段为未读消息数量
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
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val notices = get<Notices>()
    notices.getNotices(loginUser.id, begin, count).map(Notice::id).let { call.respond(it) }
}

@Serializable
private data class NoticeResponse(
    val id: NoticeId,
    val user: UserId,
    val type: Type,
    val post: PostId?,
    val count: Long?,
    val content: String?
)
private suspend fun Context.getNotice()
{
    val id = call.parameters["id"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val notices = get<Notices>()
    val user = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val notice = notices.getNotice(id)?.takeIf { it.user == user.id } ?: return call.respond(HttpStatus.NotFound)
    /**
     * 注意由于[Notice.type]不在构造函数中等问题, 无法序列化, 故手动转为[NoticeResponse]
     */
    val response = when (notice)
    {
        is PostNotice -> NoticeResponse(notice.id, notice.user, notice.type, notice.post, notice.count, null)
        is CountNotice -> NoticeResponse(notice.id, notice.user, notice.type, null, notice.count, null)
        is SystemNotice -> NoticeResponse(notice.id, notice.user, notice.type, null, null, notice.content)
    }
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