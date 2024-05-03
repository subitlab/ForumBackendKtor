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
import subit.dataClasses.*
import subit.database.*
import subit.router.Context
import subit.router.authenticated
import subit.router.get
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
    val users = get<Users>()
    val operations = get<Operations>()

    checkPermission { hasGlobalAdmin() }
    val createUser = call.receive<CreateUser>()
    checkUserInfo(createUser.username, createUser.password, createUser.email).apply {
        if (this != HttpStatus.OK) return call.respond(this)
    }
    users.createUser(
        username = createUser.username,
        password = createUser.password,
        email = createUser.email,
    ).apply {
        return if (this == null) call.respond(HttpStatus.EmailExist)
        else
        {
            operations.addOperation(getLoginUser()!!.id, createUser)
            call.respond(HttpStatus.OK)
        }
    }
}

@Serializable
private data class ProhibitUser(val id: UserId, val prohibit: Boolean, val time: Long, val reason: String)
private suspend fun Context.prohibitUser()
{
    val users = get<Users>()
    val prohibits = get<Prohibits>()
    val operations = get<Operations>()

    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val prohibitUser = call.receive<ProhibitUser>()
    val user = users.getUser(prohibitUser.id) ?: return call.respond(HttpStatus.NotFound)
    if (loginUser.permission < PermissionLevel.ADMIN || loginUser.permission <= user.permission)
        return call.respond(HttpStatus.Forbidden)
    if (prohibitUser.prohibit) prohibits.addProhibit(
        Prohibit(
            user = prohibitUser.id,
            time = prohibitUser.time,
            reason = prohibitUser.reason,
            operator = loginUser.id
        )
    )
    else prohibits.removeProhibit(prohibitUser.id)
    operations.addOperation(loginUser.id, prohibitUser)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.prohibitList()
{
    checkPermission { hasGlobalAdmin() }
    val begin = call.parameters["begin"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val count = call.parameters["count"]?.toIntOrNull() ?: return call.respond(HttpStatus.BadRequest)
    call.respond(get<Prohibits>().getProhibitList(begin, count))
}

@Serializable
private data class ChangePermission(val id: UserId, val permission: PermissionLevel)
private suspend fun Context.changePermission()
{
    val users = get<Users>()
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val changePermission = call.receive<ChangePermission>()
    val user = users.getUser(changePermission.id) ?: return call.respond(HttpStatus.NotFound)
    if (loginUser.permission < PermissionLevel.ADMIN || loginUser.permission <= user.permission)
        return call.respond(HttpStatus.Forbidden)
    users.changePermission(changePermission.id, changePermission.permission)
    get<Operations>().addOperation(loginUser.id, changePermission)
    if (loginUser.id != changePermission.id) get<Notices>().createNotice(Notice.makeSystemNotice(
        user = changePermission.id,
        content = "您的全局权限已被修改"
    ))
    call.respond(HttpStatus.OK)
}
