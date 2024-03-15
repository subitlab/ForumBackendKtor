package subit.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import subit.database.*

@Serializable data class Process(val pid: Long = 0L, val allow: Boolean = false)

fun Routing.adminPost() = authenticate()
{
    route("/admin/post")
    {
        checkPermission { it.post>=Permission.ADMIN }
        post("/processPost") { processPost() }
        get("/getPostNeedProcess") { getPostNeedProcess() }
        post("/limitBlock") { limitBlock() }
    }
}

private suspend fun Context.processPost()
{
    val process = call.receive<Process>()
    PostDatabase.query()
    {
        update(
            where = match("id" to process.pid),
            body = from("state" to if (process.allow) PostDatabase.PostState.NORMAL else PostDatabase.PostState.HIDDEN)
        )
    }
    call.respond(HttpStatusCode.OK)
}

private suspend fun Context.getPostNeedProcess()
{
    val id = call.parameters["pid"]?.toLongOrNull() ?: return call.respond(HttpStatusCode.BadRequest)
    val post = PostDatabase.query()
    {
        select(match("id" to id)).firstOrNull()?.let { deserialize<PostFull>(it) }
    } ?: return call.respond(HttpStatusCode.NotFound)
    call.respond(post)
}

private suspend fun Context.limitBlock()
{
    val id = call.parameters["bid"]?.toLongOrNull() ?: return call.respond(HttpStatusCode.BadRequest)
    val role = call.parameters["role"]?.toIntOrNull()?.let(Permission::fromLevel) ?: return call.respond(HttpStatusCode.BadRequest)
    UserDatabase.query()
    {
        update(
            where = match("id" to id),
            body = from("post" to role)
        )
    }
}