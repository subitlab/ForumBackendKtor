@file:Suppress("PackageDirectoryMismatch")

package subit.router.posts

import io.github.smiley4.ktorswaggerui.dsl.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.dataClasses.BlockId.Companion.toBlockIdOrNull
import subit.dataClasses.PostId.Companion.toPostIdOrNull
import subit.dataClasses.UserId.Companion.toUserIdOrNull
import subit.database.*
import subit.plugin.RateLimit
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.respond
import subit.utils.statuses

fun Route.posts() = route("/post", {
    tags = listOf("帖子")
}) {

    rateLimit(RateLimit.Post.rateLimitName)
    {
        post("/new", {
            description = "新建帖子"
            request {
                authenticated(true)
                body<NewPost>
                {
                    required = true
                    description = "发帖, 成功返回帖子ID."
                    example("example", NewPost("标题", "内容", false, BlockId(0), false))
                }
            }
            response {
                statuses<WarpPostId>(HttpStatus.OK, example = WarpPostId(PostId(0)))
                statuses(HttpStatus.BadRequest, HttpStatus.TooManyRequests)
            }
        }) { newPost() }

        put("/{id}", {
            description = "编辑帖子(block及以上管理员可修改)"
            request {
                authenticated(true)
                body<EditPost>
                {
                    required = true
                    description = "编辑帖子"
                    example("example", EditPost("新标题", "新内容"))
                }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.BadRequest, HttpStatus.TooManyRequests)
            }
        }) { editPost() }
    }

    get("/{id}", {
        description = "获取帖子信息"
        request {
            authenticated(false)
            pathParameter<RawPostId>("id")
            {
                required = true
                description = "要获取的帖子的id, 若是匿名帖则author始终是null"
            }
        }
        response {
            statuses<PostFull>(HttpStatus.OK, example = PostFull.example)
            statuses(HttpStatus.NotFound)
        }
    }) { getPost() }

    delete("/{id}", {
        description = "删除帖子"
        request {
            authenticated(true)
            pathParameter<RawPostId>("id")
            {
                required = true
                description = "要删除的帖子的id"
            }
        }
        response {
            statuses(HttpStatus.OK)
            statuses(HttpStatus.BadRequest)
        }
    }) { deletePost() }

    post("/{id}/like", {
        description = "点赞/点踩/取消点赞/收藏/取消收藏 帖子"
        request {
            authenticated(true)
            pathParameter<RawPostId>("id")
            {
                required = true
                description = "帖子的id"
            }
            body<LikePost>
            {
                required = true
                description = "点赞/点踩/取消点赞/收藏/取消收藏"
                example("example", LikePost(LikeType.LIKE))
            }
        }
        response {
            statuses(HttpStatus.OK)
            statuses(HttpStatus.NotFound)
        }
    }) { likePost() }

    get("/list/user/{user}", {
        description = "获取用户发送的帖子列表"
        request {
            authenticated(false)
            pathParameter<RawUserId>("user")
            {
                required = true
                description = "作者ID"
            }
            paged()
        }
        response {
            statuses<Slice<PostId>>(HttpStatus.OK, example = sliceOf(PostId(0)))
        }
    }) { getUserPosts() }

    get("/list/block/{block}", {
        description = "获取板块帖子列表"
        request {
            authenticated(false)
            pathParameter<RawBlockId>("block")
            {
                required = true
                description = "板块ID"
            }
            queryParameter<Posts.PostListSort>("sort")
            {
                required = true
                description = "排序方式"
                example = Posts.PostListSort.NEW
            }
            paged()
        }
        response {
            statuses<Slice<PostId>>(HttpStatus.OK, example = sliceOf(PostId(0)))
        }
    }) { getBlockPosts() }

    get("/top/{block}", {
        description = "获取板块置顶帖子列表"
        request {
            authenticated(false)
            pathParameter<RawBlockId>("block")
            {
                required = true
                description = "板块ID"
            }
            paged()
        }
        response {
            statuses<Slice<PostId>>(HttpStatus.OK, example = sliceOf(PostId(0)))
        }
    }) { getBlockTopPosts() }

    get("/{id}/setTop/{top}", {
        description = "设置帖子是否置顶"
        request {
            authenticated(true)
            pathParameter<RawPostId>("id")
            {
                required = true
                description = "帖子的id"
            }
            pathParameter<Boolean>("top")
            {
                required = true
                description = "是否置顶"
                example = true
            }
        }
        response {
            statuses(HttpStatus.OK)
            statuses(HttpStatus.BadRequest)
        }
    }) { setBlockTopPosts() }

    rateLimit(RateLimit.AddView.rateLimitName)
    {
        post("/view", {
            description = "增加帖子浏览量, 应在用户打开帖子时调用. 若未登陆将不会增加浏览量"
            request {
                authenticated(true)
                body<WarpPostId>
                {
                    required = true
                    description = "帖子ID"
                    example("example", WarpPostId(PostId(0)))
                }
            }
            response {
                statuses(
                    HttpStatus.OK,
                    HttpStatus.Unauthorized,
                    HttpStatus.TooManyRequests
                )
            }
        }) { addView() }
    }
}

@Serializable
data class WarpPostId(val post: PostId)

private suspend fun Context.getPost()
{
    val id = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val postInfo = get<Posts>().getPost(id) ?: return call.respond(HttpStatus.NotFound)
    val loginUser = getLoginUser()
    checkPermission { checkCanRead(postInfo) }
    val postFull = postInfo.toPostFull()
    if (!postFull.anonymous) call.respond(HttpStatus.OK, postFull) // 若不是匿名帖则直接返回
    else if (loginUser == null || loginUser.permission < PermissionLevel.ADMIN) call.respond(
        HttpStatus.OK,
        postFull.copy(
            author = UserId(
                0
            )
        )
    )
    else call.respond(HttpStatus.OK, postFull) // 若是匿名帖且用户权限足够则返回
}

@Serializable
private data class EditPost(val title: String, val content: String)

private suspend fun Context.editPost()
{
    val post = receiveAndCheckBody<EditPost>()
    val pid = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val postInfo = get<Posts>().getPost(pid) ?: return call.respond(HttpStatus.NotFound)
    if (postInfo.author != loginUser.id) checkPermission { checkHasAdminIn(postInfo.block) }
    get<Posts>().editPost(pid, post.title, post.content)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.deletePost()
{
    val id = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val post = get<Posts>().getPost(id) ?: return call.respond(HttpStatus.NotFound)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    checkPermission { checkCanDelete(post) }
    get<Posts>().setPostState(id, State.DELETED)
    if (post.author != loginUser.id) get<Notices>().createNotice(
        Notice.makeSystemNotice(
            user = post.author,
            content = "您的帖子 ${post.title} 已被删除"
        )
    )
    call.respond(HttpStatus.OK)
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
    val type = receiveAndCheckBody<LikePost>().type
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
    val block: BlockId,
    val top: Boolean
)

private suspend fun Context.newPost()
{
    val newPost = receiveAndCheckBody<NewPost>()
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)

    val block = get<Blocks>().getBlock(newPost.block) ?: return call.respond(HttpStatus.NotFound)
    checkPermission { checkCanPost(block) }

    if (newPost.anonymous) checkPermission { checkCanAnonymous(block) }
    if (newPost.top) checkPermission { checkHasAdminIn(block.id) }
    val id = get<Posts>().createPost(
        title = newPost.title,
        content = newPost.content,
        author = loginUser.id,
        anonymous = newPost.anonymous,
        block = newPost.block,
        top = newPost.top
    )
    call.respond(HttpStatus.OK, WarpPostId(id))
}

private suspend fun Context.getUserPosts()
{
    val loginUser = getLoginUser()
    val author = call.parameters["user"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val (begin, count) = call.getPage()
    val posts = get<Posts>().getUserPosts(loginUser?.id, author, begin, count)
    call.respond(HttpStatus.OK, posts)
}

private suspend fun Context.getBlockPosts()
{
    val block = call.parameters["block"]?.toBlockIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val blockFull = get<Blocks>().getBlock(block) ?: return call.respond(HttpStatus.NotFound)
    checkPermission { checkCanRead(blockFull) }
    val type = call.parameters["sort"]
                   ?.runCatching { Posts.PostListSort.valueOf(this) }
                   ?.getOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val (begin, count) = call.getPage()
    val posts = get<Posts>().getBlockPosts(block, type, begin, count)
    call.respond(HttpStatus.OK, posts)
}

private suspend fun Context.getBlockTopPosts()
{
    val block = call.parameters["block"]?.toBlockIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val blockFull = get<Blocks>().getBlock(block) ?: return call.respond(HttpStatus.NotFound)
    checkPermission { checkCanRead(blockFull) }
    val (begin, count) = call.getPage()
    val posts = get<Posts>().getBlockTopPosts(block, begin, count)
    call.respond(HttpStatus.OK, posts)
}

private suspend fun Context.setBlockTopPosts()
{
    val pid = call.parameters["id"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val top = call.parameters["top"]?.toBooleanStrictOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val postInfo = get<Posts>().getPost(pid) ?: return call.respond(HttpStatus.NotFound)
    checkPermission { checkHasAdminIn(postInfo.block) }
    get<Posts>().editPost(pid, top = top)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.addView()
{
    getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val pid = receiveAndCheckBody<WarpPostId>()
    get<Posts>().addView(pid.post)
    call.respond(HttpStatus.OK)
}