@file:Suppress("PackageDirectoryMismatch")

package subit.router.privateChat

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.PrivateChat
import subit.dataClasses.Slice
import subit.dataClasses.UserId
import subit.dataClasses.UserId.Companion.toUserIdOrNull
import subit.database.PrivateChats
import subit.database.receiveAndCheckBody
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.respond
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
                statuses<Slice<UserId>>(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
            }
        }) { getPrivateChatUsers() }

        get("/listChat/{userId}", {
            description = "获取与某人的私信列表"
            request {
                authenticated(true)
                pathParameter<UserId>("userId") { required = true; description = "对方的id" }
                queryParameter<Long>("after")
                {
                    required = false
                    description = """
                        传入时间戳, 此项非必须的, 传入后将返回从此时间起向后从begin开始count条数据
                        
                        与before互斥, 且必须传入其中一个. 若传入此项, 返回的消息将按照时间正向排序,
                        即若begin为1, count为3, 将放回晚于after的最早的3条消息, 且这3条消息的时间依次递增
                        """.trimIndent()
                }
                queryParameter<Int>("before")
                {
                    required = false
                    description = """
                        传入时间戳, 此项非必须的, 传入后将返回从此时间起向前从begin开始count条数据
                        
                        与after互斥, 且必须传入其中一个. 若传入此项, 返回的消息将按照时间逆向排序,
                        即若begin为1, count为3, 将放回早于before的最晚的3条消息, 且这3条消息的时间依次递减
                        """.trimIndent()
                }
                paged()
            }
            response {
                statuses<Slice<PrivateChat>>(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
            }
        }) { getPrivateChats() }

        get("/unread/all", {
            description = "获取所有未读私信数量"
            request {
                authenticated(true)
            }
            response {
                statuses<UnreadCount>(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
            }
        }) { getUnreadCount(false) }

        get("/unread/{userId}", {
            description = "获取与某人的未读私信数量"
            request {
                authenticated(true)
                pathParameter<UserId>("userId") { required = true; description = "对方的id" }
            }
            response {
                statuses<UnreadCount>(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
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
    val (to, content) = receiveAndCheckBody<SendPrivateChat>()
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
    val after = call.parameters["after"]?.toLongOrNull()
    val before = call.parameters["before"]?.toLongOrNull()
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    if (after != null)
        privateChats.getPrivateChatsAfter(loginUser.id, userId, Instant.fromEpochMilliseconds(after), begin, count)
    else if (before != null)
        privateChats.getPrivateChatsBefore(loginUser.id, userId, Instant.fromEpochMilliseconds(before), begin, count)
    else call.respond(HttpStatus.BadRequest)
}

@Serializable
private data class UnreadCount(val count: Long)

private suspend fun Context.getUnreadCount(withObj: Boolean)
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)

    if (!withObj) return call.respond(UnreadCount(get<PrivateChats>().getUnreadCount(loginUser.id)))
    val userId = call.parameters["userId"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val privateChats = get<PrivateChats>()
    val count = privateChats.getUnreadCount(loginUser.id, userId)
    call.respond(UnreadCount(count))
}