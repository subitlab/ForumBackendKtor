package subit

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking
import org.h2.engine.User
import subit.database.UserDatabase
import subit.database.UserFull
import java.util.*

object JWTAuth
{
    val SECRET_KEY = "TEST" //environment.config.property("jwt.secret").getString()
    val algorithm = Algorithm.HMAC512(SECRET_KEY)
    private const val VALIDITY = 1000/*ms*/*60/*s*/*60/*m*/*24/*h*/*7/*d*/
    fun makeJwtVerifier(): JWTVerifier = JWT.require(algorithm).build()
    fun sign(name: String): Map<String, String> = mapOf("token" to makeToken(name))
    private fun makeToken(name: String): String = JWT.create()
        .withSubject("Authentication")
        .withClaim("name", name)
        .withExpiresAt(getExpiration())
        .sign(algorithm)

    private fun getExpiration() = Date(System.currentTimeMillis()+VALIDITY)
    inline fun <reified T: Principal> PipelineContext<Unit, ApplicationCall>.getPrincipal(): T? = call.principal<T>()
    suspend fun getLoginUser(name: String, password: String): UserPrincipal? = UserDatabase.finedUser<UserFull>(
        username = name,
        password = password
    )?.let(::UserPrincipal)
}

data class UserPrincipal(val info: UserFull): Principal