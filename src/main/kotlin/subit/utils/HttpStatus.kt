package subit.utils

import io.ktor.http.*

/**
 * 定义了一些出现的自定义的HTTP状态码, 更多HTTP状态码请参考[io.ktor.http.HttpStatusCode]
 */
object HttpStatus
{
    // 邮箱格式错误 400
    val EmailFormatError = HttpStatusCode.BadRequest.copy(description = "邮箱格式错误")
    // 密码格式错误 400
    val PasswordFormatError = HttpStatusCode.BadRequest.copy(description = "密码格式错误")
    // 用户名格式错误 400
    val UsernameFormatError = HttpStatusCode.BadRequest.copy(description = "用户名格式错误")
    // 操作需要登陆, 未登陆 401
    val Unauthorized = HttpStatusCode.Unauthorized.copy(description = "未登陆")
    // 密码错误 401
    val PasswordError = HttpStatusCode.Unauthorized.copy(description = "密码错误")
    // 无法创建用户, 邮箱已被注册 406
    val EmailExist = HttpStatusCode.NotAcceptable.copy(description = "无法创建用户, 邮箱已被注册")
    // 不在白名单中 401
    val NotInWhitelist = HttpStatusCode.Unauthorized.copy(description = "您的邮箱不在白名单中, 请确认您的邮箱正确且有内测资格")
    // 账户不存在 404
    val AccountNotExist = HttpStatusCode.NotFound.copy(description = "账户不存在")
    // 越权操作 403
    val Forbidden = HttpStatusCode.Forbidden.copy(description = "越权操作")
    // 邮箱验证码错误 401
    val WrongEmailCode = HttpStatusCode.Unauthorized.copy(description = "验证码错误")
    // 未找到 404
    val NotFound = HttpStatusCode.NotFound.copy(description = "内容不存在")
    // 操作成功 200
    val OK = HttpStatusCode.OK.copy(description = "操作成功")
    // 不合法的请求 400
    val BadRequest = HttpStatusCode.BadRequest.copy(description = "不合法的请求")
    // 服务器未知错误 500
    val InternalServerError = HttpStatusCode.InternalServerError.copy(description = "服务器未知错误, 请联系管理员")
}