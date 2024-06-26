@file:Suppress("PackageDirectoryMismatch")

package subit.router.block

import io.github.smiley4.ktorswaggerui.dsl.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.dataClasses.BlockId.Companion.toBlockIdOrNull
import subit.database.*
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.respond
import subit.utils.statuses

fun Route.block() = route("/block", {
    tags = listOf("板块")
})
{
    post("/new", {
        description = "创建板块"
        request {
            authenticated(true)
            body<NewBlock>
            {
                required = true
                description = "新板块信息"
                example(
                    "example", NewBlock(
                        "板块名称",
                        "板块描述",
                        BlockId(0),
                        PermissionLevel.ADMIN,
                        PermissionLevel.ADMIN,
                        PermissionLevel.ADMIN,
                        PermissionLevel.ADMIN
                    )
                )
            }
        }
        response {
            statuses<WarpBlockId>(HttpStatus.OK)
            statuses(HttpStatus.Forbidden, HttpStatus.Unauthorized)
        }
    }) { newBlock() }

    put("/{id}", {
        description = "修改板块信息"
        request {
            authenticated(true)
            pathParameter<RawBlockId>("id")
            {
                required = true
                description = "板块ID"
            }
            body<EditBlockInfo>
            {
                required = true
                description = "新板块信息"
                example("example", EditBlockInfo(name = "板块名称", description = "板块描述"))
            }
        }
        response {
            statuses(HttpStatus.OK, HttpStatus.Forbidden, HttpStatus.Unauthorized)
        }
    }) { editBlockInfo() }

    get("/{id}", {
        description = "获取板块信息"
        request {
            authenticated(false)
            pathParameter<RawBlockId>("id")
            {
                required = true
                description = "板块ID"
            }
        }
        response {
            statuses<BlockFull>(HttpStatus.OK)
            statuses(HttpStatus.Forbidden, HttpStatus.Unauthorized)
        }
    }) { getBlockInfo() }

    delete("/{id}", {
        description = "删除板块"
        request {
            authenticated(true)
            pathParameter<RawBlockId>("id")
            {
                required = true
                description = "板块ID"
            }
        }
        response {
            statuses(HttpStatus.OK, HttpStatus.Forbidden, HttpStatus.Unauthorized)
        }
    }) { deleteBlock() }

    post("/changePermission", {
        description = "修改用户在板块的权限"
        request {
            authenticated(true)
            body<ChangePermission>
            {
                required = true
                description = "新权限"
                example("example", ChangePermission(UserId(0), BlockId(0), PermissionLevel.ADMIN))
            }
        }
        response {
            statuses(HttpStatus.OK, HttpStatus.Forbidden, HttpStatus.Unauthorized)
        }
    }) { changePermission() }

    get("/{id}/children", {
        description = "获取板块的子板块, 若id为0则表示获取没有父板块的板块"
        request {
            authenticated(false)
            paged()
            pathParameter<RawBlockId>("id")
            {
                required = true
                description = "板块ID"
            }
        }
        response {
            statuses<Slice<BlockId>>(HttpStatus.OK, example = sliceOf(BlockId(0)))
            statuses(HttpStatus.Forbidden, HttpStatus.Unauthorized)
        }
    }) { getChildren() }
}

@Serializable
private data class WarpBlockId(val block: BlockId)

@Serializable
private data class NewBlock(
    val name: String,
    val description: String,
    val parent: BlockId,
    val postingPermission: PermissionLevel,
    val commentingPermission: PermissionLevel,
    val readingPermission: PermissionLevel,
    val anonymousPermission: PermissionLevel,
)

private suspend fun Context.newBlock()
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val newBlock = receiveAndCheckBody<NewBlock>()
    checkPermission { checkHasAdminIn(newBlock.parent) }
    val blocks = get<Blocks>()
    blocks.getBlock(newBlock.parent) ?: return call.respond(HttpStatus.BadRequest)
    val id = blocks.createBlock(
        name = newBlock.name,
        description = newBlock.description,
        parent = newBlock.parent,
        creator = loginUser.id,
        postingPermission = newBlock.postingPermission,
        commentingPermission = newBlock.commentingPermission,
        readingPermission = newBlock.readingPermission,
        anonymousPermission = newBlock.anonymousPermission
    )
    get<Operations>().addOperation(loginUser.id, newBlock)
    call.respond(HttpStatus.OK, WarpBlockId(id))
}

@Serializable
private data class EditBlockInfo(
    val name: String? = null,
    val description: String? = null,
    val parent: BlockId? = null,
    val postingPermission: PermissionLevel? = null,
    val commentingPermission: PermissionLevel? = null,
    val readingPermission: PermissionLevel? = null,
    val anonymousPermission: PermissionLevel? = null,
)

private suspend fun Context.editBlockInfo()
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val id = call.parameters["id"]?.toBlockIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val editBlockInfo = receiveAndCheckBody<EditBlockInfo>()
    checkPermission { checkHasAdminIn(id) }
    get<Blocks>().setPermission(
        block = id,
        posting = editBlockInfo.postingPermission,
        commenting = editBlockInfo.commentingPermission,
        reading = editBlockInfo.readingPermission,
        anonymous = editBlockInfo.anonymousPermission
    )
    get<Operations>().addOperation(loginUser.id, editBlockInfo)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.getBlockInfo()
{
    val id = call.parameters["id"]?.toBlockIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val block = get<Blocks>().getBlock(id) ?: return call.respond(HttpStatus.NotFound)
    checkPermission { checkCanRead(block) }
    call.respond(HttpStatus.OK, block)
}

private suspend fun Context.deleteBlock()
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val id = call.parameters["id"]?.toBlockIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    checkPermission { checkHasAdminIn(id) }
    val blocks = get<Blocks>()
    val block = blocks.getBlock(id) ?: return call.respond(HttpStatus.NotFound)
    blocks.setState(id, State.DELETED)
    get<Operations>().addOperation(loginUser.id, id)
    if (loginUser.id != block.creator) get<Notices>().createNotice(
        Notice.makeSystemNotice(
            user = block.creator,
            content = "您的板块 ${block.name} 已被删除"
        )
    )
    call.respond(HttpStatus.OK)
}

@Serializable
private data class ChangePermission(
    val user: UserId,
    val block: BlockId,
    val permission: PermissionLevel
)

private suspend fun Context.changePermission()
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val changePermission = receiveAndCheckBody<ChangePermission>()
    checkPermission { checkHasAdminIn(changePermission.block) }
    get<Permissions>().setPermission(
        bid = changePermission.block,
        uid = changePermission.user,
        permission = changePermission.permission
    )
    get<Operations>().addOperation(loginUser.id, changePermission)
    get<Notices>().createNotice(
        Notice.makeSystemNotice(
            user = changePermission.user,
            content = "您在板块 ${get<Blocks>().getBlock(changePermission.block)?.name} 的权限已被修改"
        )
    )
    call.respond(HttpStatus.OK)
}

private suspend fun Context.getChildren()
{
    val id1 = call.parameters["id"]?.toBlockIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val id = if (id1 == BlockId(0)) null else id1
    val (begin, count) = call.getPage()
    val blocks = get<Blocks>()

    checkPermission()
    {
        val block = id?.let { blocks.getBlock(it) }
        if (block != null) checkCanRead(block)
    }

    blocks.getChildren(getLoginUser()?.id, id, begin, count).let { call.respond(HttpStatus.OK, it) }
}