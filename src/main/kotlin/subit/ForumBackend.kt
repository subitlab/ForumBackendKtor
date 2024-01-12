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
    val environment
        get() = application.environment
    val config
        get() = environment.config
    val database by lazy()
    {
        Database.connect(
            user = config.property("user").getString(),
            password = config.property("password").getString(),
            url = config.property("url").getString(),
            driver = config.property("driver").getString()
        )
    }

    @JvmStatic
    fun main(args: Array<String>)
    {
        Loader.init()
        EngineMain.main(args)
    }

    fun Application.module()
    {

        application = this
        installAuthentication()
        installDeserialization()

        routing()
        {
        }
    }

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