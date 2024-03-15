package subit.router

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import subit.JWTAuth

fun Routing.auth() = authenticate()
{
    route("/auth")
    {
        post("/login") { login() }
    }
}

data class LoginInfo(val username: String, val password: String)
private suspend fun Context.login()
{
    val loginInfo = call.receive<LoginInfo>()
    call.respond(JWTAuth.sign(loginInfo.username, loginInfo.password))
}