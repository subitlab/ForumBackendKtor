package subit

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import subit.dataClasses.UserFull
import subit.dataClasses.UserId
import subit.database.UserDatabase
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
    private lateinit var SECRET_KEY: String

    /**
     * JWT算法
     */
    private lateinit var algorithm: Algorithm

    /**
     * JWT有效期
     */
    private const val VALIDITY: Long = 1000L/*ms*/*60/*s*/*60/*m*/*24/*h*/*7/*d*/
    fun initJwtAuth(config: ApplicationConfig)
    {
        // 从配置文件中读取密钥
        SECRET_KEY = config.property("jwt.secret").getString()
        // 初始化JWT算法
        algorithm = Algorithm.HMAC512(SECRET_KEY)
    }

    /**
     * 生成验证器
     */
    fun makeJwtVerifier(): JWTVerifier = JWT.require(algorithm).build()

    /**
     * 生成Token
     * @param id 用户ID
     * @param password 用户密码(加密后)
     */
    fun makeTokenByEncryptPassword(id: UserId, password: String): Token = JWT.create()
        .withSubject("Authentication")
        .withClaim("id", id)
        .withClaim("password", password)
        .withExpiresAt(getExpiration())
        .sign(algorithm)
        .let(::Token)

    fun makeToken(id: UserId, password: String): Token = makeTokenByEncryptPassword(id, encryptPassword(password))

    suspend fun makeToken(id: UserId): Token? = UserDatabase.makeJwtToken(id)
    suspend fun makeToken(email: String): Token? = UserDatabase.makeJwtToken(email)

    private fun getExpiration() = Date(System.currentTimeMillis()+VALIDITY)
    fun PipelineContext<*, ApplicationCall>.getLoginUser(): UserFull? = call.principal<UserFull>()

    /**
     * 在数据库中保存密码的加密,暂未实现,现在为不加密,明文存储
     */
    fun encryptPassword(password: String): String = password
}
