package subit

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.h2.engine.User
import subit.database.UserDatabase
import subit.database.UserFull
import java.util.*

/**
 * JWT验证
 */
object JWTAuth
{
    @Serializable
    data class Token(val token: String)
    data class UserPrincipal(val info: UserFull): Principal

    /**
     * JWT密钥
     */
    private val SECRET_KEY = ForumBackend.config.property("jwt.secret").getString()

    /**
     * JWT算法
     */
    private val algorithm = Algorithm.HMAC512(SECRET_KEY)

    /**
     * JWT有效期
     */
    private const val VALIDITY = 1000/*ms*/*60/*s*/*60/*m*/*24/*h*/*7/*d*/

    /**
     * 生成验证器
     */
    fun makeJwtVerifier(): JWTVerifier = JWT.require(algorithm).build()
    fun sign(name: String, password: String) = Token(makeToken(name, password))
    private fun makeToken(name: String, password: String): String = JWT.create()
        .withSubject("Authentication")
        .withClaim("name", name)
        .withClaim("password", password)
        .withExpiresAt(getExpiration())
        .sign(algorithm)

    private fun getExpiration() = Date(System.currentTimeMillis()+VALIDITY)
    inline fun <reified T: Principal> PipelineContext<Unit, ApplicationCall>.getPrincipal(): T? = call.principal<T>()
    suspend fun getLoginUser(name: String, password: String): UserPrincipal? = UserDatabase.finedUser<UserFull>(
        username = name,
        password = password
    )?.let(::UserPrincipal)
}
