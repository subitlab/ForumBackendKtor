@file:Suppress("PackageDirectoryMismatch")
package subit.router.privateChat

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.PrivateChat
import subit.dataClasses.Slice
import subit.dataClasses.UserId
import subit.database.PrivateChats
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.statuses

fun Route.privateChat()
{
    route("/privateChat")
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
        }) { getPrivateChats() }

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

private suspend fun Context.getPrivateChats()
{
    val privateChats = get<PrivateChats>()
    val (begin, count) = call.getPage()
    val userId = getLoginUser()?.id ?: return call.respond(HttpStatus.Unauthorized)
    val chats = privateChats.getChatUsers(userId, begin, count)
    call.respond(chats)
}

private suspend fun Context.getPrivateChats(userId: UserId)
{
    val privateChats = get<PrivateChats>()
    val (begin, count) = call.getPage()
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val chats = privateChats.getPrivateChats(loginUser.id, userId, begin, count)
    call.respond(chats)
}
