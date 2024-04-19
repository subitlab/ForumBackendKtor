@file:Suppress("PackageDirectoryMismatch")

package subit.router.admin

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.PermissionLevel
import subit.dataClasses.Prohibit
import subit.dataClasses.Slice
import subit.dataClasses.UserId
import subit.database.AdminOperationDatabase
import subit.database.ProhibitDatabase
import subit.database.UserDatabase
import subit.database.checkPermission
import subit.router.Context
import subit.router.authenticated
import subit.router.paged
import subit.utils.HttpStatus
import subit.utils.checkUserInfo
import subit.utils.respond
import subit.utils.statuses

fun Route.admin()
{
    route("/admin", {
        tags = listOf("用户管理")
        request {
            authenticated(true)
        }
        response {
            statuses(HttpStatus.Unauthorized, HttpStatus.Forbidden)
        }
    })
    {
        post("/createUser", {
            description = "创建用户, 需要超级管理员权限, 使用此接口创建用户无需邮箱验证码, 但需要邮箱为学校邮箱"
            request {
                body<CreateUser> { required = true; description = "新用户信息" }
            }
            response {
                statuses(
                    HttpStatus.OK,
                    HttpStatus.EmailExist,
                    HttpStatus.EmailFormatError,
                    HttpStatus.UsernameFormatError,
                    HttpStatus.PasswordFormatError,
                )
            }
        }) { createUser() }

        post("/prohibitUser", {
            description = "封禁用户, 需要当前用户的权限大于ADMIN且大于对方的权限"
            request {
                body<ProhibitUser> { required = true; description = "封禁信息, 其中time是封禁结束的时间戳" }
            }
            response {
                statuses(HttpStatus.OK)
            }
        }) { prohibitUser() }

        get("/prohibitList", {
            description = "获取禁言列表, 需要当前用户的user权限大于ADMIN"
            request {
                paged()
            }
            response {
                statuses<Slice<Prohibit>>(HttpStatus.OK)
            }
        }) { prohibitList() }

        post("/changePermission", {
            description = "修改用户权限, 需要当前用户的权限大于ADMIN且大于对方的权限"
            request {
                body<ChangePermission> { required = true; description = "修改信息" }
            }
            response {
                statuses(HttpStatus.OK)
            }
        }) { changePermission() }
    }
}

@Serializable
private data class CreateUser(val username: String, val password: String, val email: String)

private suspend fun Context.createUser()
{
    checkPermission { hasGlobalAdmin() }
    val createUser = call.receive<CreateUser>()
    checkUserInfo(createUser.username, createUser.password, createUser.email).apply {
        if (this != HttpStatus.OK) return call.respond(this)
    }
    UserDatabase.createUser(
        username = createUser.username,
        password = createUser.password,
        email = createUser.email,
    ).apply {
        return if (this == null) call.respond(HttpStatus.EmailExist)
        else
        {
            AdminOperationDatabase.addOperation(getLoginUser()!!.id, createUser)
            call.respond(HttpStatus.OK)
        }
    }
}

@Serializable
private data class ProhibitUser(val id: UserId, val prohibit: Boolean, val time: Long, val reason: String)

private suspend fun Context.prohibitUser()
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val prohibitUser = call.receive<ProhibitUser>()
    val user = UserDatabase.getUser(prohibitUser.id) ?: return call.respond(HttpStatus.NotFound)
    if (loginUser.permission < PermissionLevel.ADMIN || loginUser.permission <= user.permission)
        return call.respond(HttpStatus.Forbidden)
    if (prohibitUser.prohibit) ProhibitDatabase.addProhibit(
        Prohibit(
            user = prohibitUser.id,
            time = prohibitUser.time,
            reason = prohibitUser.reason,
            operator = loginUser.id
        )
    )
    else ProhibitDatabase.removeProhibit(prohibitUser.id)
    AdminOperationDatabase.addOperation(loginUser.id, prohibitUser)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.prohibitList()
{
    checkPermission { hasGlobalAdmin() }
    val begin = call.parameters["begin"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val count = call.parameters["count"]?.toIntOrNull() ?: return call.respond(HttpStatus.BadRequest)
    call.respond(ProhibitDatabase.getProhibitList(begin, count))
}

@Serializable
private data class ChangePermission(val id: UserId, val permission: PermissionLevel)

private suspend fun Context.changePermission()
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val changePermission = call.receive<ChangePermission>()
    val user = UserDatabase.getUser(changePermission.id) ?: return call.respond(HttpStatus.NotFound)
    if (loginUser.permission < PermissionLevel.ADMIN || loginUser.permission <= user.permission)
        return call.respond(HttpStatus.Forbidden)
    UserDatabase.changePermission(changePermission.id, changePermission.permission)
    AdminOperationDatabase.addOperation(loginUser.id, changePermission)
    call.respond(HttpStatus.OK)
}
