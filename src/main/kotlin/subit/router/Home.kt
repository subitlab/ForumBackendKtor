@file:Suppress("PackageDirectoryMismatch")

package subit.router.home

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.database.Blocks
import subit.database.Posts
import subit.database.Users
import subit.plugin.RateLimit
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.respond
import subit.utils.statuses

fun Route.home() = route("/home", {
    tags = listOf("首页")
})
{
    get("/recommend", {
        description = "获取首页推荐帖子"
        request {
            queryParameter<Int>("count")
            {
                required = false
                description = "获取数量, 不填为10"
                example = 10
            }
        }
        response {
            statuses<Slice<PostId>>(HttpStatus.OK, example = sliceOf(PostId(0)))
            statuses(HttpStatus.NotFound)
        }
    }) { getHotPosts() }

    rateLimit(RateLimit.Search.rateLimitName)
    {
        route("/search", {
            request {
                authenticated(false)
                queryParameter<String>("key")
                {
                    required = true
                    description = "关键字"
                    example = "关键字"
                }
                paged()
            }
            response {
                statuses(HttpStatus.TooManyRequests)
            }
        })
        {
            get("/user", {
                description = "搜索用户 会返回所有用户名包含key的用户"
                response {
                    statuses<Slice<UserId>>(HttpStatus.OK, example = sliceOf(UserId(0)))
                }
            }) { searchUser() }

            get("/block", {
                description = "搜索板块, 会返回板块名称或介绍包含关键词的板块"
                response {
                    statuses<Slice<BlockId>>(HttpStatus.OK, example = sliceOf(BlockId(0)))
                }
            }) { searchBlock() }

            get("/post", {
                description = "搜索帖子, 会返回所有标题或内容包含关键词的帖子"
                response {
                    statuses<Slice<PostId>>(HttpStatus.OK, example = sliceOf(PostId(0)))
                }
            }) { searchPost() }
        }
    }
}

private suspend fun Context.getHotPosts()
{
    val posts = get<Posts>()
    val count = call.parameters["count"]?.toIntOrNull() ?: 10
    val result = posts.getRecommendPosts(count)
    call.respond(HttpStatusCode.OK, result)
}

private suspend fun Context.searchUser()
{
    val username = call.parameters["key"] ?: return call.respond(HttpStatus.BadRequest)
    val (begin, count) = call.getPage()
    call.respond(HttpStatus.OK, get<Users>().searchUser(username, begin, count))
}

private suspend fun Context.searchBlock()
{
    val key = call.parameters["key"] ?: return call.respond(HttpStatus.BadRequest)
    val (begin, count) = call.getPage()
    val blocks = get<Blocks>().searchBlock(getLoginUser()?.id, key, begin, count)
    call.respond(HttpStatus.OK, blocks)
}

private suspend fun Context.searchPost()
{
    val key = call.parameters["key"] ?: return call.respond(HttpStatus.BadRequest)
    val (begin, count) = call.getPage()
    val posts = get<Posts>().searchPosts(getLoginUser()?.id, key, begin, count)
    call.respond(HttpStatus.OK, posts)
}