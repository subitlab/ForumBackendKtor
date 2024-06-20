@file:Suppress("PackageDirectoryMismatch")

package subit.router.comment

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.dataClasses.CommentId.Companion.toCommentIdOrNull
import subit.dataClasses.PostId.Companion.toPostIdOrNull
import subit.database.*
import subit.plugin.RateLimit
import subit.router.Context
import subit.router.authenticated
import subit.router.get
import subit.utils.HttpStatus
import subit.utils.respond
import subit.utils.statuses

fun Route.comment() = route("/comment", {
    tags = listOf("评论")
})
{
    rateLimit(RateLimit.Post.rateLimitName)
    {
        post("/post/{postId}", {
            description = "评论一个帖子"
            request {
                authenticated(true)
                pathParameter<RawPostId>("postId")
                {
                    required = true
                    description = "帖子id"
                }
                body<CommentContent>
                {
                    description = "评论内容"
                    example("example", CommentContent("评论内容"))
                }
            }
            response {
                statuses<CommentIdResponse>(HttpStatus.OK, example = CommentIdResponse(CommentId(0)))
                statuses(HttpStatus.Forbidden, HttpStatus.NotFound)
            }
        }) { commentPost() }

        post("/comment/{commentId}", {
            description = "评论一个评论"
            request {
                authenticated(true)
                pathParameter<RawCommentId>("commentId")
                {
                    required = true
                    description = "评论id"
                }
                body<CommentContent>
                {
                    description = "评论内容"
                    example("example", CommentContent("评论内容"))
                }
            }
            response {
                statuses<CommentIdResponse>(HttpStatus.OK, example = CommentIdResponse(CommentId(0)))
                statuses(HttpStatus.Forbidden, HttpStatus.NotFound)
            }
        }) { commentComment() }
    }

    delete("/{commentId}", {
        description = "删除一个评论, 需要板块管理员权限"
        request {
            authenticated(true)
            pathParameter<RawCommentId>("commentId")
            {
                required = true
                description = "评论id"
            }
        }
        response {
            statuses(HttpStatus.OK)
            statuses(HttpStatus.Forbidden, HttpStatus.NotFound)
        }
    }) { deleteComment() }

    get("/post/{postId}", {
        description = "获取一个帖子的评论列表"
        request {
            authenticated(false)
            pathParameter<RawPostId>("postId")
            {
                required = true
                description = "帖子id"
            }
        }
        response {
            statuses<List<CommentId>>(HttpStatus.OK, example = listOf(CommentId(0)))
            statuses(HttpStatus.NotFound)
        }
    }) { getPostComments() }

    get("/comment/{commentId}", {
        description = "获取一个评论的评论列表"
        request {
            authenticated(false)
            pathParameter<RawCommentId>("commentId")
            {
                required = true
                description = "评论id"
            }
        }
        response {
            statuses<List<CommentId>>(HttpStatus.OK, example = listOf(CommentId(0)))
            statuses(HttpStatus.NotFound)
        }
    }) { getCommentComments() }

    get("/{commentId}", {
        description = "获取一个评论的信息"
        request {
            authenticated(false)
            pathParameter<RawCommentId>("commentId")
            {
                required = true
                description = "评论id"
            }
        }
        response {
            statuses<Comment>(HttpStatus.OK, example = Comment.example)
            statuses(HttpStatus.NotFound)
        }
    }) { getComment() }
}

@Serializable
private data class CommentContent(val content: String)

@Serializable
private data class CommentIdResponse(val id: CommentId)

private suspend fun Context.commentPost()
{
    val postId = call.parameters["postId"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val content = receiveAndCheckBody<CommentContent>().content
    val author = get<Posts>().getPost(postId)?.let { postInfo ->
        checkPermission { checkCanComment(postInfo) }
        postInfo.author
    } ?: return call.respond(HttpStatus.NotFound)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)

    get<Comments>().createComment(post = postId, parent = null, author = loginUser.id, content = content)
    ?: return call.respond(HttpStatus.NotFound)

    if (loginUser.id != author) get<Notices>().createNotice(
        Notice.makeObjectMessage(
            type = Notice.Type.POST_COMMENT,
            user = author,
            obj = postId,
        )
    )

    call.respond(HttpStatus.OK)
}

private suspend fun Context.commentComment()
{
    val commentId = call.parameters["commentId"]?.toCommentIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val content = receiveAndCheckBody<CommentContent>().content
    val author = get<Comments>().getComment(commentId)?.let { comment ->
        get<Posts>().getPost(comment.post)?.let { postInfo ->
            checkPermission { checkCanComment(postInfo) }
        }
        comment.author
    } ?: return call.respond(HttpStatus.NotFound)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)

    get<Comments>().createComment(post = null, parent = commentId, author = loginUser.id, content = content)
    ?: return call.respond(HttpStatus.NotFound)

    if (loginUser.id != author) get<Notices>().createNotice(
        Notice.makeObjectMessage(
            type = Notice.Type.COMMENT_REPLY,
            user = author,
            obj = commentId,
        )
    )

    call.respond(HttpStatus.OK)
}

private suspend fun Context.deleteComment()
{
    val commentId = call.parameters["commentId"]?.toCommentIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    get<Comments>().getComment(commentId)?.let { comment ->
        get<Posts>().getPost(comment.post)?.let { postInfo ->
            checkPermission { checkCanDelete(postInfo) }
        }
    } ?: return call.respond(HttpStatus.NotFound)
    get<Comments>().setCommentState(commentId, State.DELETED)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.getPostComments()
{
    val postId = call.parameters["postId"]?.toPostIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    get<Posts>().getPost(postId)?.let { postInfo ->
        checkPermission { checkCanRead(postInfo) }
    } ?: return call.respond(HttpStatus.NotFound)
    get<Comments>().getComments(post = postId)?.map(Comment::id)?.let { call.respond(HttpStatus.OK, it) }
    ?: call.respond(HttpStatus.NotFound)
}

private suspend fun Context.getCommentComments()
{
    val commentId = call.parameters["commentId"]?.toCommentIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    get<Comments>().getComment(commentId)?.let { comment ->
        get<Posts>().getPost(comment.post)?.let { postInfo ->
            checkPermission { checkCanRead(postInfo) }
        }
    } ?: return call.respond(HttpStatus.NotFound)
    get<Comments>().getComments(parent = commentId)
        ?.map(Comment::id)
        ?.let { call.respond(HttpStatus.OK, it) } ?: call.respond(HttpStatus.NotFound)
}

private suspend fun Context.getComment()
{
    val commentId = call.parameters["commentId"]?.toCommentIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val comment = get<Comments>().getComment(commentId) ?: return call.respond(HttpStatus.NotFound)
    get<Posts>().getPost(comment.post)?.let { postInfo ->
        checkPermission { checkCanRead(postInfo) }
    } ?: return call.respond(HttpStatus.NotFound)
    if (comment.state != State.NORMAL) checkPermission { checkHasGlobalAdmin() }
    call.respond(HttpStatus.OK, comment)
}
