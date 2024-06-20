package subit

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import subit.console.SimpleAnsiColor.Companion.CYAN
import subit.console.SimpleAnsiColor.Companion.RED
import subit.dataClasses.UserFull
import subit.dataClasses.UserId
import subit.database.Users
import subit.logger.ForumLogger
import java.util.*

/**
 * JWT验证
 */
@Suppress("MemberVisibilityCanBePrivate")
object JWTAuth: KoinComponent
{
    private val logger = ForumLogger.getLogger()
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
            logger.info("${CYAN}jwt.secret${RED} not found in config file, use random secret key")
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
     * @param encryptedPassword 用户密码(加密后)
     */
    private fun makeTokenByEncryptedPassword(id: UserId, encryptedPassword: String): Token = JWT.create()
        .withSubject("Authentication")
        .withClaim("id", id.value)
        .withClaim("password", encryptedPassword)
        .withExpiresAt(getExpiration())
        .sign(algorithm)
        .let(::Token)

    private val users: Users by inject()

    suspend fun checkLoginByEncryptedPassword(id: UserId, encryptedPassword: String): Boolean =
        users.getEncryptedPassword(id) == encryptedPassword

    suspend fun checkLogin(id: UserId, password: String): Boolean =
        users.getEncryptedPassword(id)?.let { verifyPassword(password, it) } ?: false
    suspend fun checkLogin(email: String, password: String): Boolean =
        users.getEncryptedPassword(email)?.let { verifyPassword(password, it) } ?: false

    suspend fun makeToken(id: UserId): Token? =
        users.getEncryptedPassword(id)?.let { makeTokenByEncryptedPassword(id, it) }

    private fun getExpiration() = Date(System.currentTimeMillis()+VALIDITY)
    fun PipelineContext<*, ApplicationCall>.getLoginUser(): UserFull? = call.principal<UserFull>()

    private val hasher = BCrypt.with(BCrypt.Version.VERSION_2B)
    private val verifier = BCrypt.verifyer(BCrypt.Version.VERSION_2B)

    /**
     * 在数据库中保存密码的加密
     */
    fun encryptPassword(password: String): String = hasher.hashToString(12, password.toCharArray())
    fun verifyPassword(password: String, hash: String): Boolean = verifier.verify(password.toCharArray(), hash).verified
}
