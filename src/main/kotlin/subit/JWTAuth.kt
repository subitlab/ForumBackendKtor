package subit

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.select
import subit.database.UserDatabase
import subit.database.UserFull
import subit.database.deserialize
import subit.database.match
import java.util.*

/**
 * JWT验证
 */
object JWTAuth
{
    @Serializable
    data class Token(val token: String)

    /**
     * JWT密钥
     */
    private val SECRET_KEY: String = ForumBackend.config.property("jwt.secret").getString()

    /**
     * JWT算法
     */
    private val algorithm: Algorithm = Algorithm.HMAC512(SECRET_KEY)

    /**
     * JWT有效期
     */
    private const val VALIDITY: Long = 1000L/*ms*/*60/*s*/*60/*m*/*24/*h*/*7/*d*/

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
    fun PipelineContext<*, ApplicationCall>.getLoginUser(): UserFull? = call.principal<UserFull>()
    suspend fun getLoginUser(name: String, password: String): UserFull? = UserDatabase.query()
    {
        select(match("username" to name, "password" to password)).firstOrNull()?.let { deserialize<UserFull>(it) }
    }

    /**
     * 在数据库中保存密码的加密,暂未实现,现在为不加密,明文存储
     */
    fun encryptPassword(password: String): String = password
}
