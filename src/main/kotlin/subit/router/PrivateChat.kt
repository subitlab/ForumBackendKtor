@file:Suppress("PackageDirectoryMismatch")
package subit.router.privateChat

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.database.PrivateChats
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.statuses

fun Route.privateChat()
{
    route("/privateChat", {
        tags = listOf("私信")
    })
    {
        post("/send", {
            description = "发送私信"
            request {
                authenticated(true)
                body<SendPrivateChat> { required = true; description = "私信内容" }
            }
            response {
                statuses(HttpStatus.OK, HttpStatus.Unauthorized)
            }
        }) { sendPrivateChat() }

        get("/listUser", {
            description = "获取私信列表"
            request {
                authenticated(true)
                paged()
            }
            response {
                statuses<Slice<UserId>>(HttpStatus.OK, HttpStatus.Unauthorized)
            }
        }) { getPrivateChatUsers() }

        get("/listChat/{userId}", {
            description = "获取与某人的私信列表"
            request {
                authenticated(true)
                pathParameter<UserId>("userId") { required = true; description = "对方的id" }
                paged()
            }
            response {
                statuses<Slice<PrivateChat>>(HttpStatus.OK, HttpStatus.Unauthorized)
            }
        }) { getPrivateChats() }

        get("/unread/all", {
            description = "获取所有未读私信数量"
            request {
                authenticated(true)
            }
            response {
                statuses<UnreadCount>(HttpStatus.OK, HttpStatus.Unauthorized)
            }
        }) { getUnreadCount(false) }

        get("/unread/{userId}", {
            description = "获取与某人的未读私信数量"
            request {
                authenticated(true)
                pathParameter<UserId>("userId") { required = true; description = "对方的id" }
            }
            response {
                statuses<UnreadCount>(HttpStatus.OK, HttpStatus.Unauthorized)
            }
        }) { getUnreadCount(true) }
    }
}

@Serializable
private data class SendPrivateChat(
    val to: UserId,
    val content: String
)
private suspend fun Context.sendPrivateChat()
{
    val (to, content) = call.receive<SendPrivateChat>()
    val from = getLoginUser()?.id ?: return call.respond(HttpStatus.Unauthorized)
    val privateChats = get<PrivateChats>()
    privateChats.addPrivateChat(from, to, content)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.getPrivateChatUsers()
{
    val privateChats = get<PrivateChats>()
    val (begin, count) = call.getPage()
    val userId = getLoginUser()?.id ?: return call.respond(HttpStatus.Unauthorized)
    val chats = privateChats.getChatUsers(userId, begin, count)
    call.respond(chats)
}

private suspend fun Context.getPrivateChats()
{
    val privateChats = get<PrivateChats>()
    val (begin, count) = call.getPage()
    val userId = call.parameters["userId"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val chats = privateChats.getPrivateChats(loginUser.id, userId, begin, count)
    call.respond(chats)
}

@JvmInline
@Serializable
private value class UnreadCount(val count: Long)
private suspend fun Context.getUnreadCount(withObj: Boolean)
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)

    if (!withObj) return call.respond(UnreadCount(get<PrivateChats>().getUnreadCount(loginUser.id)))

    val userId = call.parameters["userId"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val privateChats = get<PrivateChats>()
    val count = privateChats.getUnreadCount(loginUser.id, userId)
    call.respond(UnreadCount(count))
}