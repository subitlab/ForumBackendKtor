package subit

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database

object ForumBackend
{
    private lateinit var application: Application
    val environment // 环境
        get() = application.environment
    val config // application.yaml
        get() = environment.config
    val database by lazy() // 数据库
    {
        Database.connect(
            user = config.property("user").getString(),
            password = config.property("password").getString(),
            url = config.property("url").getString(),
            driver = config.property("driver").getString()
        )
    }

    /**
     * 论坛,启动!
     */
    @JvmStatic
    fun main(args: Array<String>)
    {
        Loader.init()
        EngineMain.main(args)
    }

    /**
     * 应用程序入口
     */
    fun Application.module()
    {
        application = this
        installAuthentication()
        installDeserialization()

        routing()
        {
        }
    }

    /**
     * 安装登陆验证服务
     */
    private fun Application.installAuthentication() = install(Authentication)
    {
        jwt()
        {
            verifier(JWTAuth.makeJwtVerifier()) // 设置验证器
            validate() // 设置验证函数
            {
                JWTAuth.getLoginUser(it.payload.getClaim("name").asString(), it.payload.getClaim("password").asString())
            }
        }
    }

    /**
     * 安装反序列化/序列化服务
     */
    private fun Application.installDeserialization() = install(ContentNegotiation)
    {
        json(Json()
        {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}