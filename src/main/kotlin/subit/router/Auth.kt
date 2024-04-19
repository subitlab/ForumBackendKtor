@file:Suppress("PackageDirectoryMismatch")
package subit.router.auth

import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth
import subit.JWTAuth.getLoginUser
import subit.dataClasses.UserId
import subit.database.EmailCodeDatabase
import subit.database.UserDatabase
import subit.database.WhitelistDatabase
import subit.router.Context
import subit.router.authenticated
import subit.utils.*

fun Route.auth()
{
    route("/auth",{
        tags = listOf("账户")
    })
    {
        post("/register", {
            description = "注册, 若成功返回token"
            request {
                body<RegisterInfo> { required = true; description = "注册信息" }
            }
            this.response {
                statuses<JWTAuth.Token>(HttpStatus.OK)
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
                body<LoginInfo> { required = true; description = "登陆信息" }
            }
            this.response {
                statuses<JWTAuth.Token>(HttpStatus.OK)
                statuses(
                    HttpStatus.PasswordError,
                    HttpStatus.AccountNotExist,
                )
            }
        }) { login() }

        post("/loginByCode",{
            description = "通过邮箱验证码登陆, 若成功返回token"
            request {
                body<LoginByCodeInfo> { required = true; description = "登陆信息" }
            }
            this.response {
                statuses<JWTAuth.Token>(HttpStatus.OK)
                statuses(
                    HttpStatus.AccountNotExist,
                    HttpStatus.WrongEmailCode,
                )
            }
        }) { loginByCode() }

        post("/resetPassword",{
            description = "重置密码(忘记密码)"
            request {
                body<ResetPasswordInfo> { required = true; description = "重置密码信息" }
            }
            this.response {
                statuses(HttpStatus.OK)
                statuses(
                    HttpStatus.WrongEmailCode,
                    HttpStatus.AccountNotExist,
                )
            }
        }){ resetPassword() }

        post("/sendEmailCode",{
            description = "发送邮箱验证码"
            request {
                body<EmailInfo> { required = true; description = "邮箱信息" }
            }
            this.response {
                statuses(HttpStatus.OK)
                statuses(
                    HttpStatus.EmailFormatError,
                )
            }
        }) { sendEmailCode() }

        post("/changePassword",{
            description = "修改密码"
            request {
                authenticated(true)
                body<ChangePasswordInfo> { required = true; description = "修改密码信息" }
            }
            this.response {
                statuses<JWTAuth.Token>(HttpStatus.OK)
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
    val registerInfo: RegisterInfo = call.receive()
    // 检查用户名、密码、邮箱是否合法
    checkUserInfo(registerInfo.username, registerInfo.password, registerInfo.email).apply {
        if (this != HttpStatus.OK) return call.respond(this)
    }
    if (!WhitelistDatabase.isWhitelisted(registerInfo.email)) return call.respond(HttpStatus.NotInWhitelist)
    // 验证邮箱验证码
    if (!EmailCodeDatabase.verifyEmailCode(
            registerInfo.email,
            registerInfo.code,
            EmailCodeDatabase.EmailCodeUsage.REGISTER
        )
    ) return call.respond(HttpStatus.WrongEmailCode)
    // 创建用户
    UserDatabase.createUser(
        username = registerInfo.username,
        password = registerInfo.password,
        email = registerInfo.email,
    ).apply {
        return if (this == null) call.respond(HttpStatus.EmailExist)
        else call.respond(JWTAuth.makeToken(this, registerInfo.password))
    }
}

@Serializable
private data class LoginInfo(val email: String? = null, val id: UserId? = null, val password: String)
private suspend fun Context.login()
{
    val loginInfo = call.receive<LoginInfo>()
    val user = if (loginInfo.id != null)
        UserDatabase.checkUserLogin(loginInfo.id, loginInfo.password)
    else if (loginInfo.email != null)
        UserDatabase.checkUserLogin(loginInfo.email, loginInfo.password)
    else return call.respond(HttpStatus.BadRequest)
    if (user == null) return call.respond(HttpStatus.NotFound)
    if (!user.first) return call.respond(HttpStatus.PasswordError)
    return call.respond(JWTAuth.makeToken(user.second.id, loginInfo.password))
}

@Serializable
private data class LoginByCodeInfo(val email: String? = null, val id: UserId? = null, val code: String)
private suspend fun Context.loginByCode()
{
    val loginInfo = call.receive<LoginByCodeInfo>()
    val email =
        loginInfo.email ?: // 若email不为空，直接使用email
        loginInfo.id?.let {
            UserDatabase.getUser(it)?.email // email为空，尝试从id获取email
                ?: return call.respond(HttpStatus.AccountNotExist)
        } // 若id不存在，返回登陆失败
        ?: return call.respond(HttpStatus.BadRequest) // id和email都为空，返回错误的请求
    if (!EmailCodeDatabase.verifyEmailCode(email, loginInfo.code, EmailCodeDatabase.EmailCodeUsage.LOGIN))
        return call.respond(HttpStatus.WrongEmailCode)
    JWTAuth.makeToken(email).apply {
        if (this != null) call.respond(this)
        else call.respond(HttpStatus.AccountNotExist)
    }
}

@Serializable
private data class ResetPasswordInfo(val email: String, val code: String, val password: String)
private suspend fun Context.resetPassword()
{
    // 接收重置密码的信息
    val resetPasswordInfo = call.receive<ResetPasswordInfo>()
    // 验证邮箱验证码
    if (!EmailCodeDatabase.verifyEmailCode(
            resetPasswordInfo.email,
            resetPasswordInfo.code,
            EmailCodeDatabase.EmailCodeUsage.RESET_PASSWORD
        )
    ) return call.respond(HttpStatus.WrongEmailCode)
    // 重置密码
    if (UserDatabase.setPassword(resetPasswordInfo.email, resetPasswordInfo.password))
        call.respond(HttpStatus.OK)
    else
        call.respond(HttpStatus.AccountNotExist)
}

@Serializable
private data class ChangePasswordInfo(val oldPassword: String, val newPassword: String)
private suspend fun Context.changePassword()
{
    val (oldPassword, newPassword) = call.receive<ChangePasswordInfo>()
    val user = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    UserDatabase.checkUserLogin(user.id, oldPassword).apply {
        if (this == null) return call.respond(HttpStatus.PasswordError)
    }
    if (!checkPassword(newPassword)) return call.respond(HttpStatus.PasswordFormatError)
    UserDatabase.setPassword(user.email, newPassword)
    return call.respond(JWTAuth.makeToken(user.id, newPassword))
}

@Serializable
private data class EmailInfo(val email: String, val usage: EmailCodeDatabase.EmailCodeUsage)
private suspend fun Context.sendEmailCode()
{
    val emailInfo = call.receive<EmailInfo>()
    if (!checkEmail(emailInfo.email)) return call.respond(HttpStatus.EmailFormatError)
    EmailCodeDatabase.sendEmailCode(emailInfo.email, emailInfo.usage)
    call.respond(HttpStatus.OK)
}