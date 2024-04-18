@file:Suppress("PackageDirectoryMismatch")
package subit.router.posts

import io.github.smiley4.ktorswaggerui.dsl.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.database.LikesDatabase
import subit.database.PermissionDatabase.canAnonymous
import subit.database.PermissionDatabase.canDelete
import subit.database.PermissionDatabase.canPost
import subit.database.PermissionDatabase.canRead
import subit.database.PostDatabase
import subit.database.StarDatabase
import subit.router.Context
import subit.utils.HttpStatus
import subit.utils.statuses

fun Route.posts()
{
    route("post", {
        listOf("帖子")
    }) {
        get("/{id}", {
            description = "获取帖子信息, "
            request {
                pathParameter<Long>("id") { required=true;description = "要获取的帖子的id, 若是匿名帖则author始终是null" }
            }
            response {
                statuses<PostFull>(HttpStatus.OK)
                statuses(HttpStatus.NotFound)
            }
        }) { getPost() }

        delete("/{id}", {
            description = "删除帖子"
            request {
                pathParameter<Long>("id") { required=true;description = "要删除的帖子的id" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.BadRequest)
            }
        }) { deletePost() }

        put("/{id}", {
            description = "编辑帖子"
            request {
                body<EditPost> { required=true;description = "编辑帖子, 成功返回帖子ID" }
            }
            response {
                statuses<Long>(HttpStatus.OK)
                statuses(HttpStatus.BadRequest)
            }
        }) { editPost() }

        post("/{id}/like", {
            description = "点赞/点踩/取消点赞/收藏/取消收藏 帖子"
            request {
                pathParameter<Long>("id") { required=true;description = "帖子的id" }
                body<LikePost> { required=true;description = "点赞/点踩/取消点赞/收藏/取消收藏" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.NotFound)
            }
        }) { likePost() }

        post("/new", {
            description = "新建帖子"
            request {
                body<NewPost> { required=true;description = "发帖, 成功返回帖子ID" }
            }
            response {
                statuses<Long>(HttpStatus.OK)
                statuses(HttpStatus.BadRequest)
            }
        }) { newPost() }

        post("/list", {
            description = "获取帖子列表"
            request {
                queryParameter<Int>("block") { required=false;description = "板块ID" }
                queryParameter<Int>("user") { required=false;description = "作者ID" }
                queryParameter<Long>("begin") { required=true;description = "起始位置" }
                queryParameter<Int>("count") { required=true;description = "获取数量" }
            }
        }) { getPosts() }
    }
}

private suspend fun Context.getPost()
{
    val id = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val postInfo = PostDatabase.getPost(id) ?: return call.respond(HttpStatus.NotFound)
    val loginUser = getLoginUser()
    if (!loginUser.canRead(postInfo)) return call.respond(HttpStatus.NotFound) // 检查用户权限
    val postFull = postInfo.toPostFull()
    if (!postFull.anonymous) call.respond(postFull) // 若不是匿名帖则直接返回
    else if (loginUser == null || loginUser.permission < PermissionLevel.ADMIN) call.respond(postFull.copy(author = 0))
    else call.respond(postFull) // 若是匿名帖且用户权限足够则返回
}

@Serializable
private data class EditPost(val title: String, val content: String)
private suspend fun Context.editPost()
{
    val post = call.receive<EditPost>()
    val pid = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val postInfo = PostDatabase.getPost(pid) ?: return call.respond(HttpStatus.NotFound)
    if (postInfo.author != loginUser.id) return call.respond(HttpStatus.Forbidden)
    PostDatabase.editPost(pid, post.title, post.content)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.deletePost()
{
    val id = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val post = PostDatabase.getPost(id) ?: return call.respond(HttpStatus.NotFound)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    if (loginUser.canDelete(post)) PostDatabase.setPostState(id, PostState.DELETED).also { call.respond(HttpStatus.OK) }
    else call.respond(HttpStatus.Forbidden)
}

@Serializable
 private enum class LikeType
{
    LIKE,
    DISLIKE,
    UNLIKE,
    STAR,
    UNSTAR
}
@Serializable
private data class LikePost(val type: LikeType)
private suspend fun Context.likePost()
{
    val id = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val post = PostDatabase.getPost(id) ?: return call.respond(HttpStatus.NotFound)
    val type = call.receive<LikePost>().type
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    if (!loginUser.canRead(post)) return call.respond(HttpStatus.NotFound) // 检查用户权限
    when (type)
    {
        LikeType.LIKE    -> LikesDatabase.like(loginUser.id, id, true)
        LikeType.DISLIKE -> LikesDatabase.like(loginUser.id, id, false)
        LikeType.UNLIKE  -> LikesDatabase.unlike(loginUser.id, id)
        LikeType.STAR    -> StarDatabase.addStar(loginUser.id, id)
        LikeType.UNSTAR  -> StarDatabase.removeStar(loginUser.id, id)
    }
    call.respond(HttpStatus.OK)
}

@Serializable
private data class NewPost(val title: String, val content: String, val anonymous: Boolean, val block: Int)
private suspend fun Context.newPost()
{
    val post = call.receive<PostInfo>()
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    if (!loginUser.canPost(post.block)) return call.respond(HttpStatus.Forbidden)
    if (!loginUser.canAnonymous(post.block)) return call.respond(HttpStatus.Forbidden)
    val id = PostDatabase.createPost(
        title = post.title,
        content = post.content,
        author = loginUser.id,
        anonymous = post.anonymous,
        block = post.block
    )
    call.respond(id)
}

private suspend fun Context.getPosts()
{
    val loginUser = getLoginUser()
    val block = call.parameters["block"]?.toIntOrNull()
    val author = call.parameters["user"]?.toUserIdOrNull()
    val begin = call.parameters["begin"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val count = call.parameters["count"]?.toIntOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val posts = PostDatabase.getPosts(loginUser, block, author, begin, count)
    call.respond(posts)
}
