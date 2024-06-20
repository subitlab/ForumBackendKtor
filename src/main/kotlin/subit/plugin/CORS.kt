package subit.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import subit.debug

/**
 * 安装跨域请求相关处理, 此功能主要是为了前端开发时方便调试, 故仅在debug模式下开启
 */
fun Application.installCORS() = install(CORS)
{
    if (debug)
    {
        anyHost()
        HttpMethod.DefaultMethods.forEach { allowMethod(it) }
        allowCredentials = true
        allowNonSimpleContentTypes = true
    }
}