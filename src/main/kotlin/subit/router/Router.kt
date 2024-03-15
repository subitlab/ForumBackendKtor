package subit.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import subit.JWTAuth.getLoginUser
import subit.database.UserFull

typealias Context = PipelineContext<*, ApplicationCall>

fun Route.checkPermission(body: (UserFull)->Boolean) = intercept(ApplicationCallPipeline.Call) { checkPermission(body) }
suspend inline fun Context.checkPermission(body: (UserFull)->Boolean)
{
    val user = getLoginUser()
    if (user==null)
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

fun Application.router() = routing()
{
    adminUser()
    adminPost()
}