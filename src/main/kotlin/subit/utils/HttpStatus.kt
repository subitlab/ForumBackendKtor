package subit.utils

import io.github.smiley4.ktorswaggerui.dsl.OpenApiResponses
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

/**
 * 定义了一些出现的自定义的HTTP状态码, 更多HTTP状态码请参考[io.ktor.http.HttpStatusCode]
 */
@Suppress("unused")
data class HttpStatus(val code: HttpStatusCode, val message: String)
{
    companion object
    {
        // 邮箱格式错误 400
        val EmailFormatError = HttpStatus(HttpStatusCode.BadRequest, "邮箱格式错误")
        // 密码格式错误 400
        val PasswordFormatError = HttpStatus(HttpStatusCode.BadRequest, "密码格式错误")
        // 用户名格式错误 400
        val UsernameFormatError = HttpStatus(HttpStatusCode.BadRequest, "用户名格式错误")
        // 操作需要登陆, 未登陆 401
        val Unauthorized = HttpStatus(HttpStatusCode.Unauthorized, "未登录, 请先登录")
        // 密码错误 401
        val PasswordError = HttpStatus(HttpStatusCode.Unauthorized, "账户或密码错误")
        // 无法创建用户, 邮箱已被注册 406
        val EmailExist = HttpStatus(HttpStatusCode.NotAcceptable, "邮箱已被注册")
        // 不在白名单中 401
        val NotInWhitelist = HttpStatus(HttpStatusCode.Unauthorized, "不在白名单中, 请确认邮箱或联系管理员")
        // 账户不存在 404
        val AccountNotExist = HttpStatus(HttpStatusCode.NotFound, "账户不存在")
        // 越权操作 403
        val Forbidden = HttpStatus(HttpStatusCode.Forbidden, "权限不足")
        // 邮箱验证码错误 401
        val WrongEmailCode = HttpStatus(HttpStatusCode.Unauthorized, "邮箱验证码错误")
        // 未找到 404
        val NotFound = HttpStatus(HttpStatusCode.NotFound, "目标不存在或已失效")
        // 操作成功 200
        val OK = HttpStatus(HttpStatusCode.OK, "操作成功")
        // 不合法的请求 400
        val BadRequest = HttpStatus(HttpStatusCode.BadRequest, "不合法的请求")
        // 服务器未知错误 500
        val InternalServerError = HttpStatus(HttpStatusCode.InternalServerError, "服务器未知错误")
        // 请求体过大 413
        val PayloadTooLarge = HttpStatus(HttpStatusCode.PayloadTooLarge, "请求体过大")
        // 不支持的媒体类型 415
        val UnsupportedMediaType = HttpStatus(HttpStatusCode.UnsupportedMediaType, "不支持的媒体类型")
        // 云文件存储空间已满 406
        val NotEnoughSpace = HttpStatus(HttpStatusCode.NotAcceptable, "云文件存储空间不足")
        // 账户被封禁
        val Prohibit = HttpStatus(HttpStatusCode.Unauthorized, "账户被封禁")
        // 包含违禁词汇
        val ContainsBannedWords = HttpStatus(HttpStatusCode.NotAcceptable, "包含违禁词汇")
    }
}
@Serializable
@JvmInline
value class StatusMessage(val message: String)
val HttpStatus.statusMessage get() = StatusMessage(message)

suspend inline fun ApplicationCall.respond(status: HttpStatus) = this.respond(status.code, status.statusMessage)
suspend inline fun <reified T: Any> ApplicationCall.respond(status: HttpStatus,t: T) = this.respond(status.code, t)
fun OpenApiResponses.statuses(vararg statuses: HttpStatus, bodyDescription: String = "错误信息")
{
    statuses.forEach {
        it.code to {
            description = it.message
            body<StatusMessage> {
                description = bodyDescription
                example("固定值", it.statusMessage)
            }
        }
    }
}
@JvmName("statusesWithBody")
inline fun <reified T> OpenApiResponses.statuses(vararg statuses: HttpStatus, bodyDescription: String = "返回体")
{
    statuses.forEach {
        it.code to {
            description = it.message
            body<T> { description = bodyDescription }
        }
    }
}