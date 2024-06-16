@file:Suppress("PackageDirectoryMismatch")

package subit.router.auth

import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth
import subit.JWTAuth.getLoginUser
import subit.config.emailConfig
import subit.dataClasses.UserId
import subit.database.*
import subit.router.Context
import subit.router.authenticated
import subit.router.get
import subit.utils.*

fun Route.auth()
{
    route("/auth", {
        tags = listOf("账户")
    })
    {
        post("/register", {
            description = "注册, 若成功返回token"
            request {
                body<RegisterInfo>
                {
                    required = true
                    description = "注册信息"
                    example("example", RegisterInfo("username", "password", "email", "code"))
                }
            }
            this.response {
                statuses<JWTAuth.Token>(HttpStatus.OK, example = JWTAuth.Token("token"))
                statuses(
                    HttpStatus.WrongEmailCode,
                    HttpStatus.EmailExist,
                    HttpStatus.EmailFormatError,
                    HttpStatus.UsernameFormatError,
                    HttpStatus.PasswordFormatError,
                    HttpStatus.NotInWhitelist
                )
            }
        }) { register() }

        post("/login", {
            description = "登陆, 若成功返回token"
            request {
                body<LoginInfo>()
                {
                    required = true
                    description = "登陆信息, id(用户ID)和email(用户的邮箱)二选一"
                    example("example", LoginInfo(email = "email", password = "password", id = UserId(0)))
                }
            }
            this.response {
                statuses<JWTAuth.Token>(HttpStatus.OK, example = JWTAuth.Token("token"))
                statuses(
                    HttpStatus.PasswordError,
                    HttpStatus.AccountNotExist,
                )
            }
        }) { login() }

        post("/loginByCode", {
            description = "通过邮箱验证码登陆, 若成功返回token"
            request {
                body<LoginByCodeInfo>()
                {
                    required = true
                    description = "登陆信息, id(用户ID)和email(用户的邮箱)二选一"
                    example("example", LoginByCodeInfo(email = "email@abc.com", code = "123456"))
                }
            }
            this.response {
                statuses<JWTAuth.Token>(HttpStatus.OK, example = JWTAuth.Token("token"))
                statuses(
                    HttpStatus.AccountNotExist,
                    HttpStatus.WrongEmailCode,
                )
            }
        }) { loginByCode() }

        post("/resetPassword", {
            description = "重置密码(忘记密码)"
            request {
                body<ResetPasswordInfo>
                {
                    required = true
                    description = "重置密码信息"
                    example("example", ResetPasswordInfo("email@abc.com", "code", "newPassword"))
                }
            }
            this.response {
                statuses(HttpStatus.OK)
                statuses(
                    HttpStatus.WrongEmailCode,
                    HttpStatus.AccountNotExist,
                )
            }
        }) { resetPassword() }

        post("/sendEmailCode", {
            description = "发送邮箱验证码"
            request {
                body<EmailInfo>
                {
                    required = true
                    description = "邮箱信息"
                    example("example", EmailInfo("email@abc.com", EmailCodes.EmailCodeUsage.REGISTER))
                }
            }
            this.response {
                statuses(HttpStatus.OK)
                statuses(
                    HttpStatus.EmailFormatError,
                )
            }
        }) { sendEmailCode() }

        post("/changePassword", {
            description = "修改密码"
            request {
                authenticated(true)
                body<ChangePasswordInfo>
                {
                    required = true
                    description = "修改密码信息"
                    example("example", ChangePasswordInfo("oldPassword", "newPassword"))
                }
            }
            this.response {
                statuses<JWTAuth.Token>(HttpStatus.OK, example = JWTAuth.Token("token"))
                statuses(
                    HttpStatus.Unauthorized,
                    HttpStatus.PasswordError,
                    HttpStatus.PasswordFormatError,
                )
            }
        }) { changePassword() }
    }
}

@Serializable
private data class RegisterInfo(val username: String, val password: String, val email: String, val code: String)

private suspend fun Context.register()
{
    val registerInfo: RegisterInfo = receiveAndCheckBody()
    // 检查用户名、密码、邮箱是否合法
    checkUserInfo(registerInfo.username, registerInfo.password, registerInfo.email).apply {
        if (this != HttpStatus.OK) return call.respond(this)
    }
    if (emailConfig.enableWhiteList && !get<Whitelists>().isWhitelisted(registerInfo.email))
        return call.respond(HttpStatus.NotInWhitelist)
    // 验证邮箱验证码
    if (!get<EmailCodes>().verifyEmailCode(
            registerInfo.email,
            registerInfo.code,
            EmailCodes.EmailCodeUsage.REGISTER
        )
    ) return call.respond(HttpStatus.WrongEmailCode)
    // 创建用户
    val id = get<Users>().createUser(
        username = registerInfo.username,
        password = registerInfo.password,
        email = registerInfo.email,
    ) ?: return call.respond(HttpStatus.EmailExist)
    // 创建成功, 返回token
    val token = JWTAuth.makeToken(id) ?: /*理论上不会进入此分支*/ return call.respond(HttpStatus.AccountNotExist)
    return call.respond(HttpStatus.OK, token)
}

@Serializable
private data class LoginInfo(val email: String? = null, val id: UserId? = null, val password: String)

private suspend fun Context.login()
{
    val users = get<Users>()
    val loginInfo = receiveAndCheckBody<LoginInfo>()
    val checked = if (loginInfo.id != null) JWTAuth.checkLogin(loginInfo.id, loginInfo.password)
    else if (loginInfo.email != null) JWTAuth.checkLogin(loginInfo.email, loginInfo.password)
    else return call.respond(HttpStatus.BadRequest)
    // 若登陆失败，返回错误信息
    if (!checked) return call.respond(HttpStatus.PasswordError)
    val id = loginInfo.id
             ?: users.getUser(loginInfo.email!!)?.id
             ?: /*理论上不会进入此分支*/ return call.respond(HttpStatus.AccountNotExist)
    val token = JWTAuth.makeToken(id) ?: /*理论上不会进入此分支*/ return call.respond(HttpStatus.AccountNotExist)
    return call.respond(HttpStatus.OK, token)
}

@Serializable
private data class LoginByCodeInfo(val email: String? = null, val id: UserId? = null, val code: String)

private suspend fun Context.loginByCode()
{
    val loginInfo = receiveAndCheckBody<LoginByCodeInfo>()
    val email =
        loginInfo.email ?: // 若email不为空，直接使用email
        loginInfo.id?.let {
            get<Users>().getUser(it)?.email // email为空，尝试从id获取email
            ?: return call.respond(HttpStatus.AccountNotExist)
        } // 若id不存在，返回登陆失败
        ?: return call.respond(HttpStatus.BadRequest) // id和email都为空，返回错误的请求
    if (!get<EmailCodes>().verifyEmailCode(email, loginInfo.code, EmailCodes.EmailCodeUsage.LOGIN))
        return call.respond(HttpStatus.WrongEmailCode)
    val user = get<Users>().getUser(email) ?: return call.respond(HttpStatus.AccountNotExist)
    val token = JWTAuth.makeToken(user.id) ?: /*理论上不会进入此分支*/ return call.respond(HttpStatus.AccountNotExist)
    return call.respond(HttpStatus.OK, token)
}

@Serializable
private data class ResetPasswordInfo(val email: String, val code: String, val password: String)

private suspend fun Context.resetPassword()
{
    // 接收重置密码的信息
    val resetPasswordInfo = receiveAndCheckBody<ResetPasswordInfo>()
    // 验证邮箱验证码
    if (!get<EmailCodes>().verifyEmailCode(
            resetPasswordInfo.email,
            resetPasswordInfo.code,
            EmailCodes.EmailCodeUsage.RESET_PASSWORD
        )
    ) return call.respond(HttpStatus.WrongEmailCode)
    // 重置密码
    if (get<Users>().setPassword(resetPasswordInfo.email, resetPasswordInfo.password))
        call.respond(HttpStatus.OK)
    else
        call.respond(HttpStatus.AccountNotExist)
}

@Serializable
private data class ChangePasswordInfo(val oldPassword: String, val newPassword: String)

private suspend fun Context.changePassword()
{
    val users = get<Users>()

    val (oldPassword, newPassword) = receiveAndCheckBody<ChangePasswordInfo>()
    val user = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    if (!JWTAuth.checkLogin(user.id, oldPassword)) return call.respond(HttpStatus.PasswordError)
    if (!checkPassword(newPassword)) return call.respond(HttpStatus.PasswordFormatError)
    users.setPassword(user.email, newPassword)
    val token = JWTAuth.makeToken(user.id) ?: /*理论上不会进入此分支*/ return call.respond(HttpStatus.AccountNotExist)
    return call.respond(HttpStatus.OK, token)
}

@Serializable
private data class EmailInfo(val email: String, val usage: EmailCodes.EmailCodeUsage)

private suspend fun Context.sendEmailCode()
{
    val emailInfo = receiveAndCheckBody<EmailInfo>()
    if (!checkEmail(emailInfo.email))
        return call.respond(HttpStatus.EmailFormatError)
    val emailCodes = get<EmailCodes>()
    if (!emailCodes.canSendEmail(emailInfo.email, emailInfo.usage))
        return call.respond(HttpStatus.SendEmailCodeTooFrequent)
    emailCodes.sendEmailCode(emailInfo.email, emailInfo.usage)
    call.respond(HttpStatus.OK)
}