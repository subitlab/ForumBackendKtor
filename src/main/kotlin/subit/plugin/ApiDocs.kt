package subit.plugin

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*

/**
 * 在/api-docs 路径下安装SwaggerUI
 */
fun Application.installApiDoc() = install(SwaggerUI)
{
    swagger()
    {
        swaggerUrl = "api-docs"
        // 当直接访问根目录时跳转到/api-docs 因为根目录下没有内容
        forwardRoot = true
        // apidocs为了不对外暴露, 因此需要认证, 见Authentication插件
        authentication = "auth-api-docs"
    }
    info()
    {
        title = "论坛后端API文档"
        version = subit.version
        description = "SubIT论坛后端API文档"
    }
    this.ignoredRouteSelectors += RateLimitRouteSelector::class
}