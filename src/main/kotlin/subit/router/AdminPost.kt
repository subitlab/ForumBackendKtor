package subit.router

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.database.BlockDatabase
import subit.database.PermissionLevel
import subit.database.PostDatabase
import subit.database.PostFull
import subit.utils.HttpStatus

fun Route.adminPost() = authenticate()
{
    route("/admin/post",{
        tags = listOf("帖子管理")
        description = "帖子管理接口"
        response {
            addHttpStatuses(HttpStatus.Unauthorized, HttpStatus.Forbidden)
        }
    })
    {
        checkPermission { it.post>=PermissionLevel.ADMIN }
        post("/processPost",{
            description = "审核帖子, 需要管理员权限"
            request {
                body<ProcessPost> { description = "审核信息" }
            }
            response {
                addHttpStatuses(HttpStatus.OK)
            }
        }) { processPost() }

        get("/getPostNeedProcess",{
            description = "获取需要审核的帖子, 需要管理员权限"
            request {
                pathParameter<Long>("pid") { description = "帖子ID" }
            }
            response {
                addHttpStatuses<PostFull>(HttpStatus.OK)
                addHttpStatuses(HttpStatus.NotFound)
            }
        }) { getPostNeedProcess() }

        post("/limitBlock",{
            description = "限制板块发帖权限, 需要管理员权限"
            request {
                body<LimitBlock> { description = "限制信息" }
            }
            response {
                addHttpStatuses(HttpStatus.OK)
            }
        }) { limitBlock() }
    }
}

@Serializable
data class ProcessPost(val pid: Long, val allow: Boolean)
private suspend fun Context.processPost()
{
    val process = call.receive<ProcessPost>()
    PostDatabase.setPostState(
        process.pid,
        if (process.allow) PostDatabase.PostState.NORMAL else PostDatabase.PostState.HIDDEN
    )
    call.respond(HttpStatus.OK)
}

private suspend fun Context.getPostNeedProcess()
{
    val id = call.parameters["pid"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val post = PostDatabase.getPostFull(id) ?: return call.respond(HttpStatus.NotFound)
    call.respond(post)
}

@Serializable
data class LimitBlock(val bid: Int, val permission: PermissionLevel)
private suspend fun Context.limitBlock()
{
    val data = call.receive<LimitBlock>()
    BlockDatabase.setPostingPermission(data.bid, data.permission)
    call.respond(HttpStatus.OK)
}