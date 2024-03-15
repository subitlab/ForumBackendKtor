package subit.router

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.update
import subit.database.Permission
import subit.database.UserDatabase
import subit.database.from
import subit.database.match

@Serializable data class NewAdminUser(val id: ULong)

fun Routing.adminUser() = authenticate()
{
    route("/admin/user")
    {
        checkPermission { it.post>=Permission.ADMIN }
        post("newAdminUser") { newAdminUser() }
    }
}

private suspend fun Context.newAdminUser()
{
    checkPermission { it.post>=Permission.SUPER_ADMIN }
    val newAdminUser = call.receive<NewAdminUser>()
    UserDatabase.query()
    {
        update(
            where = match("id" to newAdminUser.id),
            body = from("post" to Permission.ADMIN.toLevel())
        )
    }
}

