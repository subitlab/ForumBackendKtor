package subit.router

import io.github.smiley4.ktorswaggerui.dsl.OpenApiRequest
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import subit.JWTAuth.getLoginUser
import subit.config.systemConfig
import subit.database.Prohibits
import subit.database.checkParameters
import subit.router.admin.admin
import subit.router.auth.auth
import subit.router.bannedWords.bannedWords
import subit.router.block.block
import subit.router.comment.comment
import subit.router.files.files
import subit.router.home.home
import subit.router.notice.notice
import subit.router.posts.posts
import subit.router.privateChat.privateChat
import subit.router.report.report
import subit.router.user.user
import subit.utils.HttpStatus
import subit.utils.respond

typealias Context = PipelineContext<*, ApplicationCall>

inline fun <reified T: Any> Context.get(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
) = application.get<T>(qualifier, parameters)

/**
 * 辅助方法, 标记此接口需要验证token(需要登陆)
 * @param required 是否必须登陆
 */
fun OpenApiRequest.authenticated(required: Boolean) = headerParameter<String>("Authorization")
{
    this.description = "Bearer token"
    this.required = required
}

/**
 * 辅助方法, 标记此方法返回需要传入begin和count, 用于分页
 */
fun OpenApiRequest.paged()
{
    queryParameter<Long>("begin")
    {
        this.required = true
        this.description = "起始位置"
        this.example = 0L
    }
    queryParameter<Int>("count")
    {
        this.required = true
        this.description = "获取数量"
        this.example = 10
    }
}

fun ApplicationCall.getPage(): Pair<Long, Int>
{
    val begin = request.queryParameters["begin"]?.toLongOrNull() ?: 0
    val count = request.queryParameters["count"]?.toIntOrNull() ?: 10
    return begin to count
}

fun Application.router() = routing()
{
    authenticate("forum-auth", optional = true)
    {
        val prohibits: Prohibits by inject()
        intercept(ApplicationCallPipeline.Call)
        {
            //检测是否在维护中
            if (systemConfig.systemMaintaining)
            {
                call.respond(HttpStatus.Maintaining)
                finish()
            }

            // 检查用户是否被封禁
            getLoginUser()?.id?.apply {
                if (prohibits.isProhibited(this))
                {
                    call.respond(HttpStatus.Prohibit)
                    finish()
                }
            }

            // 检查参数是否包含违禁词
            checkParameters()
        }

        admin()
        auth()
        bannedWords()
        block()
        comment()
        files()
        home()
        notice()
        posts()
        privateChat()
        report()
        user()
    }
}