package subit.router

/*
fun Route.adminPost()
{
    route("/admin/post",{
        tags = listOf("帖子管理")
        description = "帖子管理接口"
        request {
            authenticated(true)
        }
        response {
            addHttpStatuses(HttpStatus.Unauthorized, HttpStatus.Forbidden)
        }
    })
    {
        checkPermission { it.permissions.post>=PermissionLevel.ADMIN }
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
                queryParameter<Long>("pid") { description = "帖子ID" }
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
    AdminOperationDatabase.addOperation(getLoginUser()!!.id,process)
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
    AdminOperationDatabase.addOperation(getLoginUser()!!.id,data)
    call.respond(HttpStatus.OK)
}

 */