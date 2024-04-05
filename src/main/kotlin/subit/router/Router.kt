package subit.router

import io.github.smiley4.ktorswaggerui.dsl.OpenApiResponses
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

@JvmName("addHttpStatusesWithBody")
inline fun <reified T> OpenApiResponses.addHttpStatuses(vararg statuses: HttpStatusCode) =
    if (T::class == Nothing::class) addHttpStatuses(*statuses)
    else statuses.forEach { it to { description = it.description; body<T>() } }

fun OpenApiResponses.addHttpStatuses(vararg statuses: HttpStatusCode) =
    statuses.forEach { it to { description = it.description } }

fun Application.router() = routing()
{
    auth()
    adminUser()
    adminPost()
}