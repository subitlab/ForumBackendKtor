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
import subit.database.*
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.statuses

fun Route.posts()
{
    route("/post", {
        tags = listOf("帖子")
    }) {
        get("/{id}", {
            description = "获取帖子信息"
            request {
                authenticated(false)
                pathParameter<Long>("id") {
                    required = true; description = "要获取的帖子的id, 若是匿名帖则author始终是null"
                }
            }
            response {
                statuses<PostFull>(HttpStatus.OK)
                statuses(HttpStatus.NotFound)
            }
        }) { getPost() }

        delete("/{id}", {
            description = "删除帖子"
            request {
                authenticated(true)
                pathParameter<Long>("id") { required = true; description = "要删除的帖子的id" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.BadRequest)
            }
        }) { deletePost() }

        put("/{id}", {
            description = "编辑帖子"
            request {
                authenticated(true)
                body<EditPost> { required = true; description = "编辑帖子, 成功返回帖子ID" }
            }
            response {
                statuses<Long>(HttpStatus.OK)
                statuses(HttpStatus.BadRequest)
            }
        }) { editPost() }

        post("/{id}/like", {
            description = "点赞/点踩/取消点赞/收藏/取消收藏 帖子"
            request {
                authenticated(true)
                pathParameter<Long>("id") { required = true; description = "帖子的id" }
                body<LikePost> { required = true; description = "点赞/点踩/取消点赞/收藏/取消收藏" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.NotFound)
            }
        }) { likePost() }

        post("/new", {
            description = "新建帖子"
            request {
                authenticated(true)
                body<NewPost> { required = true; description = "发帖, 成功返回帖子ID" }
            }
            response {
                statuses<PostInfo>(HttpStatus.OK)
                statuses(HttpStatus.BadRequest)
            }
        }) { newPost() }

        get("/list/user/{user}", {
            description = "获取用户发送的帖子列表"
            request {
                authenticated(false)
                pathParameter<UserId>("user") { required = true; description = "作者ID" }
                paged()
            }
            response {
                statuses<Slice<PostInfo>>(HttpStatus.OK)
            }
        }) { getUserPosts() }

        get("/list/block/{block}", {
            description = "获取板块帖子列表"
            request {
                authenticated(false)
                pathParameter<BlockId>("block") { required = true; description = "板块ID" }
                queryParameter<Posts.PostListSort>("sort") { required = true; description = "排序方式" }
                paged()
            }
            response {
                statuses<Slice<PostInfo>>(HttpStatus.OK)
            }
        }) { getBlockPosts() }

        get("/top/{block}", {
            description = "获取板块置顶帖子列表"
            request {
                authenticated(false)
                pathParameter<BlockId>("block") { required = true; description = "板块ID" }
                paged()
            }
            response {
                statuses<Slice<PostInfo>>(HttpStatus.OK)
            }
        }) { getBlockTopPosts() }

        get("/search", {
            description = "搜索帖子"
            request {
                authenticated(false)
                queryParameter<String>("key") { required = true; description = "关键字" }
                paged()
            }
            response {
                statuses<Slice<PostId>>(HttpStatus.OK)
            }
        }) { searchPost() }

        post("/view", {
            description = "增加帖子浏览量, 应在用户打开帖子时调用. 若未登陆将不会增加浏览量"
            request {
                authenticated(true)
                body<PostId> { required = true; description = "帖子ID" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
                statuses(HttpStatus.BadRequest)
            }
        }) { addView() }
    }
}

private suspend fun Context.getPost()
{
    val id = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val postInfo = get<Posts>().getPost(id) ?: return call.respond(HttpStatus.NotFound)
    val loginUser = getLoginUser()
    checkPermission { checkCanRead(postInfo) }
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
    val postInfo = get<Posts>().getPost(pid) ?: return call.respond(HttpStatus.NotFound)
    if (postInfo.author != loginUser.id) return call.respond(HttpStatus.Forbidden)
    get<Posts>().editPost(pid, post.title, post.content)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.deletePost()
{
    val id = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val post = get<Posts>().getPost(id) ?: return call.respond(HttpStatus.NotFound)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    checkPermission { checkCanDelete(post) }
    get<Posts>().setPostState(id, State.DELETED).also { call.respond(HttpStatus.OK) }
    if (post.author != loginUser.id) get<Notices>().createNotice(
        Notice.makeSystemNotice(
            user = post.author,
            content = "您的帖子 ${post.title} 已被删除"
        )
    )
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
    val post = get<Posts>().getPost(id) ?: return call.respond(HttpStatus.NotFound)
    val type = call.receive<LikePost>().type
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    checkPermission { checkCanRead(post) }
    when (type)
    {
        LikeType.LIKE    -> get<Likes>().like(loginUser.id, id, true)
        LikeType.DISLIKE -> get<Likes>().like(loginUser.id, id, false)
        LikeType.UNLIKE  -> get<Likes>().unlike(loginUser.id, id)
        LikeType.STAR    -> get<Stars>().addStar(loginUser.id, id)
        LikeType.UNSTAR  -> get<Stars>().removeStar(loginUser.id, id)
    }
    if (loginUser.id != post.author && (type == LikeType.LIKE || type == LikeType.STAR))
        get<Notices>().createNotice(
            Notice.makeObjectMessage(
                type = if (type == LikeType.LIKE) Notice.Type.LIKE else Notice.Type.STAR,
                user = post.author,
                obj = id
            )
        )
    call.respond(HttpStatus.OK)
}

@Serializable
private data class NewPost(
    val title: String,
    val content: String,
    val anonymous: Boolean,
    val block: Int,
    val top: Boolean
)

private suspend fun Context.newPost()
{
    val post = call.receive<NewPost>()
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    checkPermission { checkCanPost(post.block) }
    if (post.anonymous) checkPermission { checkCanAnonymous(post.block) }
    if (post.top) checkPermission { checkHasAdminIn(post.block) }
    val id = get<Posts>().createPost(
        title = post.title,
        content = post.content,
        author = loginUser.id,
        anonymous = post.anonymous,
        block = post.block,
        top = post.top
    )
    get<Posts>().getPost(id)?.let { call.respond(it) } ?: call.respond(HttpStatus.InternalServerError)
}

private suspend fun Context.getUserPosts()
{
    val loginUser = getLoginUser()
    val author = call.parameters["user"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val (begin, count) = call.getPage()
    val posts = get<Posts>().getUserPosts(loginUser, author, begin, count)
    call.respond(posts)
}

private suspend fun Context.getBlockPosts()
{
    val block = call.parameters["block"]?.toBlockIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val type = call.parameters["sort"]?.let(Posts.PostListSort::valueOf) ?: return call.respond(HttpStatus.BadRequest)
    val (begin, count) = call.getPage()
    checkPermission { checkCanRead(block) }
    val posts = get<Posts>().getBlockPosts(block, type, begin, count)
    call.respond(posts)
}

private suspend fun Context.getBlockTopPosts()
{
    val block = call.parameters["block"]?.toBlockIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val (begin, count) = call.getPage()
    checkPermission { checkCanRead(block) }
    val posts = get<Posts>().getBlockTopPosts(block, begin, count)
    call.respond(posts)
}

private suspend fun Context.searchPost()
{
    val key = call.parameters["key"] ?: return call.respond(HttpStatus.BadRequest)
    val (begin, count) = call.getPage()
    val posts = get<Posts>().searchPosts(getLoginUser()?.id, key, begin, count).map(PostInfo::id)
    call.respond(posts)
}

private suspend fun Context.addView()
{
    val pid = call.receive<PostId>()
    get<Posts>().addView(pid)
    call.respond(HttpStatus.OK)
}