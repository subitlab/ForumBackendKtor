package subit.router

import io.github.smiley4.ktorswaggerui.dsl.OpenApiRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import subit.JWTAuth.getLoginUser
import subit.dataClasses.UserFull
import subit.database.ProhibitDatabase
import subit.router.admin.admin
import subit.router.auth.auth
import subit.router.files.files
import subit.router.posts.posts
import subit.router.user.user
import subit.utils.HttpStatus

typealias Context = PipelineContext<*, ApplicationCall>

fun Route.checkPermission(body: (UserFull)->Boolean) = intercept(ApplicationCallPipeline.Call) { checkPermission(body) }
suspend inline fun Context.checkPermission(body: (UserFull)->Boolean)
{
    val user = getLoginUser()
    if (user == null)
    {
        call.respond(HttpStatusCode.Unauthorized)
        finish()
    }
    else if (!body(user))
    {
        call.respond(HttpStatusCode.Forbidden)
        finish()
    }
}

/**
 * 辅助方法, 标记此接口需要验证token(需要登陆)
 * @param required 是否必须登陆
 */
fun OpenApiRequest.authenticated(required: Boolean) = headerParameter<String>("Authorization")
{
    this.description = "Bearer token"
    this.required = required
}

fun Application.router() = routing()
{
    authenticate(optional = true)
    {
        intercept(ApplicationCallPipeline.Call)
        {
            getLoginUser()?.id?.apply {
                ProhibitDatabase.checkProhibit(this).apply {
                    call.respond(HttpStatus.Forbidden)
                    finish()
                }
            }
        }

        admin()
        auth()
        // todo block()
        files()
        // todo files()
        // todo notice()
        posts()
        user()
    }
}