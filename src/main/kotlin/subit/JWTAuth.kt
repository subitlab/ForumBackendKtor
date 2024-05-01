package subit

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import subit.console.SimpleAnsiColor.Companion.CYAN
import subit.console.SimpleAnsiColor.Companion.RED
import subit.dataClasses.UserFull
import subit.dataClasses.UserId
import subit.logger.ForumLogger
import java.util.*

/**
 * JWT验证
 */
object JWTAuth: KoinComponent
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
    fun Application.initJwtAuth()
    {
        // 从配置文件中读取密钥
        val key = environment.config.propertyOrNull("jwt.secret")?.getString()
        if (key == null)
        {
            ForumLogger.info("${CYAN}jwt.secret${RED} not found in config file, use random secret key")
            SECRET_KEY = UUID.randomUUID().toString()
        }
        else
        {
            SECRET_KEY = key
        }
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
    private fun makeTokenByEncryptPassword(id: UserId, password: String): Token = JWT.create()
        .withSubject("Authentication")
        .withClaim("id", id)
        .withClaim("password", password)
        .withExpiresAt(getExpiration())
        .sign(algorithm)
        .let(::Token)

    fun makeToken(id: UserId, password: String): Token = makeTokenByEncryptPassword(id, encryptPassword(password))

    private fun getExpiration() = Date(System.currentTimeMillis()+VALIDITY)
    fun PipelineContext<*, ApplicationCall>.getLoginUser(): UserFull? = call.principal<UserFull>()

    /**
     * 在数据库中保存密码的加密,暂未实现,现在为不加密,明文存储
     */
    fun encryptPassword(password: String): String = password
}
