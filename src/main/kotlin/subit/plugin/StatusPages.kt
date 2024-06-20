package subit.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import subit.logger.ForumLogger
import subit.utils.HttpStatus
import subit.utils.respond
import kotlin.time.Duration.Companion.seconds

/**
 * 对于不同的状态码返回不同的页面
 */
fun Application.installStatusPages() = install(StatusPages)
{
    val logger = ForumLogger.getLogger()
    exception<BadRequestException> { call, _ -> call.respond(HttpStatus.BadRequest) }
    exception<Throwable>
    { call, throwable ->
        ForumLogger.getLogger("ForumBackend.installStatusPages")
            .warning("出现位置错误, 访问接口: ${call.request.path()}", throwable)
        call.respond(HttpStatus.InternalServerError)
    }
    /** 包装一层, 因为正常的返回没有body, 但是这里需要返回一个body, 见[HttpStatus] */
    status(HttpStatusCode.NotFound) { _ -> call.respond(HttpStatus.NotFound) }
    status(HttpStatusCode.Unauthorized) { _ -> call.respond(HttpStatus.Unauthorized) }
    status(HttpStatusCode.Forbidden) { _ -> call.respond(HttpStatus.Forbidden) }
    status(HttpStatusCode.BadRequest) { _ -> call.respond(HttpStatus.BadRequest) }
    status(HttpStatusCode.InternalServerError) { _ -> call.respond(HttpStatus.InternalServerError) }

    /** 针对请求过于频繁的处理, 详见[RateLimit] */
    status(HttpStatusCode.TooManyRequests) { _ ->
        val time = call.response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.seconds
        val typeName = call.response.headers["X-RateLimit-Type"]
        val type = RateLimit.list.find { it.rawRateLimitName == typeName }
        logger.config("TooManyRequests with type: $type($typeName), retryAfter: $time")
        if (time == null)
            return@status call.respond(HttpStatus.TooManyRequests)
        if (type == null)
            return@status call.respond(HttpStatus.TooManyRequests.copy(message = "请求过于频繁, 请${time}后再试"))
        type.customResponse(call, time)
    }
}