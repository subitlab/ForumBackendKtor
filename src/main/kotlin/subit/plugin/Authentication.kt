package subit.plugin

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.inject
import subit.JWTAuth
import subit.JWTAuth.initJwtAuth
import subit.config.apiDocsConfig
import subit.dataClasses.UserId
import subit.database.Users
import subit.logger.ForumLogger

/**
 * 安装登陆验证服务
 */
fun Application.installAuthentication() = install(Authentication)
{
    // 初始化jwt验证
    this@installAuthentication.initJwtAuth()
    // jwt验证, 这个验证是用于论坛正常的用户登陆
    jwt("forum-auth")
    {
        verifier(JWTAuth.makeJwtVerifier()) // 设置验证器
        validate() // 设置验证函数
        {
            val users: Users by inject()
            ForumLogger.getLogger("ForumBackend.installAuthentication").config(
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

    // 此登陆仅用于api文档的访问, 见ApiDocs插件
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