package subit

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fusesource.jansi.AnsiConsole
import subit.console.Console
import subit.console.command.CommandSet
import subit.database.DatabaseSingleton.initDatabase
import subit.database.UserDatabase
import subit.logger.ForumLogger
import subit.router.router
import subit.utils.HttpStatus

object ForumBackend
{
    lateinit var version: String
        private set

    /**
     * 论坛,启动!
     */
    @JvmStatic
    fun main(args: Array<String>)
    {
        AnsiConsole.systemInstall() // 支持终端颜色码
        CommandSet.registerAll() // 注册所有命令
        Console.init() // 初始化终端(启动命令处理线程)
        EngineMain.main(args) // 启动ktor
    }

    /**
     * 应用程序入口
     */
    @Suppress("unused")
    fun Application.module()
    {
        version = environment.config.property("version").getString()
        initDatabase(environment.config)

        installAuthentication()
        installDeserialization()
        installStatusPages()
        installApiDoc()

        router()
    }

    /**
     * 安装登陆验证服务
     */
    private fun Application.installAuthentication() = install(Authentication)
    {
        JWTAuth.initJwtAuth(this@installAuthentication.environment.config) // 初始化JWT验证
        jwt()
        {
            verifier(JWTAuth.makeJwtVerifier()) // 设置验证器
            validate() // 设置验证函数
            {
                ForumLogger.config("用户token: id=${it.payload.getClaim("id").asInt()}, password=${it.payload.getClaim("password").asString()}")
                val (b, user) = UserDatabase.checkUserLoginByEncryptedPassword(
                    it.payload.getClaim("id").asInt(),
                    it.payload.getClaim("password").asString()
                ) ?: return@validate null
                if (b) user
                else null
            }
        }

        @Serializable
        data class ApiDocsUser(val name: String = "username", val password: String = "password")
        var apiDocsUser: ApiDocsUser = Loader.getConfigOrCreate("auth-api-docs.yml", ApiDocsUser())
        Loader.reloadTasks.add {
            apiDocsUser = Loader.getConfigOrCreate("auth-api-docs.yml", ApiDocsUser())
            ForumLogger.config("重新加载auth-api-docs配置: $apiDocsUser")
        }

        basic("auth-api-docs")
        {
            realm = "Access to the Swagger UI"
            validate()
            {
                if (it.name == apiDocsUser.name && it.password == apiDocsUser.password)
                    UserIdPrincipal(it.name)
                else null
            }
        }
    }

    /**
     * 安装反序列化/序列化服务(用于处理json)
     */
    private fun Application.installDeserialization() = install(ContentNegotiation)
    {
        json(Json()
        {
            // 设置默认值也序列化, 否则不默认值不会被序列化
            encodeDefaults = true
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    /**
     * 对于不同的状态码返回不同的页面
     */
    private fun Application.installStatusPages() = install(StatusPages)
    {
        exception<BadRequestException> { call, _ -> call.respond(HttpStatus.BadRequest) }
        exception<Throwable>
        { call, throwable ->
            ForumLogger.warning("出现位置错误, 访问接口: ${call.request.path()}", throwable)
            call.respond(HttpStatus.InternalServerError)
        }
    }

    private fun Application.installApiDoc() = install(SwaggerUI)
    {
        swagger()
        {
            swaggerUrl = "api-docs"
            forwardRoot = true
            authentication = "auth-api-docs"
        }
        info()
        {
            title = "论坛后端API文档"
            version = ForumBackend.version
            description = "SubIT论坛后端API文档"
        }
    }
}