package subit.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import subit.router.auth.EmailInfo
import subit.router.posts.WarpPostId
import subit.utils.HttpStatus
import subit.utils.respond
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface RateLimit
{
    val rawRateLimitName: String
    val limit: Int
    val duration: Duration
    val rateLimitName: RateLimitName
        get() = RateLimitName(rawRateLimitName)
    suspend fun customResponse(call: ApplicationCall, duration: Duration)
    suspend fun getKey(call: ApplicationCall): Any

    companion object
    {
        val list = listOf(Search, Post, SendEmail, AddView)
    }

    data object Search: RateLimit
    {
        override val rawRateLimitName = "search"
        override val limit = 60
        override val duration = 15.seconds
        override suspend fun customResponse(call: ApplicationCall, duration: Duration)
        {
            call.respond(HttpStatus.TooManyRequests.copy(message = "搜索操作过于频繁, 请${duration}后再试"))
        }

        override suspend fun getKey(call: ApplicationCall): Any
        {
            val auth = call.request.headers["Authorization"]
            if (auth != null) return auth
            return call.request.local.remoteHost
        }
    }

    data object Post: RateLimit
    {
        override val rawRateLimitName = "post"
        override val limit = 1
        override val duration = 10.seconds
        override suspend fun customResponse(call: ApplicationCall, duration: Duration)
        {
            call.respond(HttpStatus.TooManyRequests.copy(message = "发布操作过于频繁, 请${duration}后再试"))
        }

        /**
         * 如果登陆了就按照登陆的用户来限制, 否则的话发帖会因未登陆而返回401(就没必要在这里限制了),
         * 所以这里用随机UUID这样相当于在这里没有限制
         */
        override suspend fun getKey(call: ApplicationCall): Any =
            call.parameters["Authorization"] ?: UUID.randomUUID()
    }

    data object SendEmail: RateLimit
    {
        override val rawRateLimitName = "sendEmail"
        override val limit = 1
        override val duration = 1.minutes
        override suspend fun customResponse(call: ApplicationCall, duration: Duration)
        {
            call.respond(HttpStatus.TooManyRequests.copy(message = "发送邮件过于频繁, 请${duration}后再试"))
        }

        /**
         * 按照请求体中的邮箱及其用途来限制. 如果接收不到请求体的话应该会返回BadRequest, 所以这里通过随机UUID来不限制
         */
        override suspend fun getKey(call: ApplicationCall): Any =
            runCatching { call.receive<EmailInfo>() }.getOrNull() ?: UUID.randomUUID()
    }

    data object AddView: RateLimit
    {
        override val rawRateLimitName = "addView"
        override val limit = 1
        override val duration = 5.minutes
        override suspend fun customResponse(call: ApplicationCall, duration: Duration)
        {
            call.respond(HttpStatus.TooManyRequests.copy(message = "添加浏览量过于频繁, 请${duration}后再试"))
        }

        override suspend fun getKey(call: ApplicationCall): Any
        {
            val auth = call.request.headers["Authorization"] ?: return UUID.randomUUID()
            val postId = call.receive<WarpPostId>().post
            return auth to postId
        }
    }
}

/**
 * 安装速率限制插件, 该插件可以限制请求的速率, 防止恶意请求
 */
fun Application.installRateLimit() = install(io.ktor.server.plugins.ratelimit.RateLimit)
{
    RateLimit.list.forEach()
    { rateLimit ->
        register(rateLimit.rateLimitName)
        {
            rateLimiter(limit = rateLimit.limit, refillPeriod = rateLimit.duration)
            requestKey { rateLimit.getKey(call = it) }
            modifyResponse { call, state ->
                call.response.headers.appendIfAbsent("X-RateLimit-Type", rateLimit.rawRateLimitName, false)
                when (state)
                {
                    is RateLimiter.State.Available ->
                    {
                        call.response.headers.appendIfAbsent("X-RateLimit-Limit", state.limit.toString())
                        call.response.headers.appendIfAbsent("X-RateLimit-Remaining", state.remainingTokens.toString())
                        call.response.headers.appendIfAbsent(
                            "X-RateLimit-Reset",
                            (state.refillAtTimeMillis / 1000).toString()
                        )
                    }

                    is RateLimiter.State.Exhausted ->
                    {
                        call.response.headers.appendIfAbsent(HttpHeaders.RetryAfter, state.toWait.inWholeSeconds.toString())
                    }
                }
            }
        }
    }
}