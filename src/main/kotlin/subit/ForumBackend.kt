package subit

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import kotlinx.serialization.json.Json
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import subit.JWTAuth.initJwtAuth
import subit.config.apiDocsConfig
import subit.console.command.CommandSet.startCommandThread
import subit.dataClasses.UserId
import subit.database.Users
import subit.database.loadDatabaseImpl
import subit.logger.ForumLogger
import subit.router.router
import subit.utils.FileUtils
import subit.utils.HttpStatus
import subit.utils.respond
import java.io.File

lateinit var version: String
    private set

fun main(args: Array<String>)
{
    ForumLogger // 初始化日志
    subit.config.ConfigLoader.init() // 初始化配置文件加载器
    val argsMap = args.mapNotNull {
        it.indexOf("=").let { idx ->
            when (idx)
            {
                -1 -> null
                else -> Pair(it.take(idx), it.drop(idx+1))
            }
        }
    }.toMap()
    val args1 = argsMap.entries.filterNot { it.key == "-config" }.map { (k, v) -> "$k=$v" }.toTypedArray()
    val configFile = File("config.yaml")
    if (!configFile.exists())
    {
        configFile.createNewFile()
        val defaultConfig =
            Loader.getResource("default_config.yaml")?.readAllBytes() ?: error("default_config.yaml not found")
        configFile.writeBytes(defaultConfig)
        ForumLogger.severe("config.yaml not found, the default config has been created, please modify it and restart the program")
        return
    }
    val customConfig = ConfigLoader.load("config.yaml")
    val environment = commandLineEnvironment(args = args1)
    {
        this.config = this.config.withFallback(customConfig)
    }
    embeddedServer(Netty, environment).start(wait = true)
}

/**
 * 应用程序入口
 */
@Suppress("unused")
fun Application.init()
{
    version = environment.config.property("version").getString()

    startCommandThread()

    FileUtils.init() // 初始化文件系统
    installAuthentication()
    installDeserialization()
    installStatusPages()
    installApiDoc()
    installKoin()

    loadDatabaseImpl()

    router()
}

/**
 * 安装登陆验证服务
 */
private fun Application.installAuthentication() = install(Authentication)
{
    this@installAuthentication.initJwtAuth()
    jwt()
    {
        verifier(JWTAuth.makeJwtVerifier()) // 设置验证器
        validate() // 设置验证函数
        {
            val users: Users by inject()
            ForumLogger.config(
                "用户token: id=${
                    it.payload.getClaim("id")
                        .asInt()
                }, password=${it.payload.getClaim("password").asString()}"
            )
            if (!JWTAuth.checkLoginByEncryptedPassword(
                    it.payload.getClaim("id").asInt().let(::UserId),
                    it.payload.getClaim("password").asString()
                )
            ) null
            else users.getUser(it.payload.getClaim("id").asInt().let(::UserId))
        }
    }

    basic("auth-api-docs")
    {
        realm = "Access to the Swagger UI"
        validate()
        {
            if (it.name == apiDocsConfig.name && it.password == apiDocsConfig.password)
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
    status(HttpStatusCode.NotFound)
    { _ ->
        call.respond(HttpStatus.NotFound)
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
        version = subit.version
        description = "SubIT论坛后端API文档"
    }
}

private fun Application.installKoin() = install(Koin)
{
    slf4jLogger()
    modules(module {
        single { this@installKoin } bind Application::class
    })
}