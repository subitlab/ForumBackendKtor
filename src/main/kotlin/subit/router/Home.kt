@file:Suppress("PackageDirectoryMismatch")

package subit.router.home

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import subit.dataClasses.PostId
import subit.dataClasses.Slice
import subit.dataClasses.sliceOf
import subit.database.Posts
import subit.router.Context
import subit.router.get
import subit.utils.HttpStatus
import subit.utils.statuses

fun Route.home()
{
    route("/home", {
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
    }
}

suspend fun Context.getHotPosts()
{
    val posts = get<Posts>()
    val count = call.parameters["count"]?.toIntOrNull() ?: 10
    val result = posts.getRecommendPosts(count)
    call.respond(HttpStatusCode.OK, result)
}