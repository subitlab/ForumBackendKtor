package subit.router

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.database.PermissionGroupForOperation
import subit.database.PermissionLevel
import subit.database.UserDatabase
import subit.utils.HttpStatus
import subit.utils.checkUserInfo

fun Route.adminUser() = authenticate()
{
    route("/admin/user", {
        tags = listOf("用户管理")
        description = "用户管理接口"
        response {
            addHttpStatuses(HttpStatus.Unauthorized, HttpStatus.Forbidden)
        }
    })
    {
        post("createUser", {
            description = "创建用户, 需要超级管理员权限, 使用此接口创建用户无需邮箱验证码, 但需要邮箱为学校邮箱"
            request {
                body<CreateUser> { description = "新用户信息" }
            }
            response {
                addHttpStatuses(
                    HttpStatus.OK,
                    HttpStatus.EmailExist,
                    HttpStatus.EmailFormatError,
                    HttpStatus.UsernameFormatError,
                    HttpStatus.PasswordFormatError,
                )
            }
        }) { createUser() }

        post("prohibitUser", {
            description = "禁言用户, 需要管理员权限。原接口文档中禁言无法取消或设定时间，需要进一步讨论"
            request {
                body<ProhibitUser> { description = "禁言信息" }
            }
            response {
                addHttpStatuses(HttpStatus.OK)
            }
        }) { prohibitUser() }

        post("changeUserPermission", {
            description = "修改用户权限, 需要管理员权限。若试图将目标的权限修改到超过自己的权限，返回禁止访问"
            request {
                body<PermissionGroupForOperation> { description = "修改权限信息" }
            }
            response {
                addHttpStatuses(HttpStatus.OK, HttpStatus.Forbidden)
            }
        }) { changeUserPermission() }

        get("getUserPermission", {
            description = "获取用户权限, 需要管理员权限。若未提供目标用户id返回不合法的请求"
            response {
                addHttpStatuses<PermissionGroupForOperation>(
                    HttpStatus.OK,
                    HttpStatus.Unauthorized,
                    HttpStatus.BadRequest,
                    HttpStatus.NotFound,
                    HttpStatus.Forbidden
                )
            }
        }) { getUserPermission() }
    }
}

@Serializable
data class CreateUser(val username: String, val password: String, val email: String)

private suspend fun Context.createUser()
{
    checkPermission { it.post >= PermissionLevel.SUPER_ADMIN }
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
        else call.respond(HttpStatus.OK)
    }
}

@Serializable
data class ProhibitUser(val studentId: Long, val prohibit: Boolean)

private suspend fun Context.prohibitUser()
{
    checkPermission { it.post >= PermissionLevel.ADMIN }
    val prohibitUser = call.receive<ProhibitUser>()
    TODO("目前接口文档中禁言无法取消或设定时间，需要进一步讨论")
}

private suspend fun Context.changeUserPermission()
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val changeUserPermission = call.receive<PermissionGroupForOperation>()
    // 若试图将目标的权限修改到超过自己的权限，返回禁止访问
    if (!loginUser.toPermission().canChange(changeUserPermission))
    {
        return call.respond(HttpStatus.Forbidden)
    }
    // 获取目标用户, 若不存在返回未找到
    val user = UserDatabase.getUser(changeUserPermission.id) ?: return call.respond(HttpStatus.NotFound)
    // 若试图修改的用户权限高于自己的权限，返回禁止访问
    if (user.id != loginUser.id &&
        !loginUser.toPermission().canChange(user.toPermission().toChangePermissionOperator())
    )
    {
        return call.respond(HttpStatus.Forbidden)
    }
    // 修改用户权限
    UserDatabase.changeUserPermission(
        changeUserPermission.id,
        changeUserPermission.read,
        changeUserPermission.post,
        changeUserPermission.comment,
        changeUserPermission.ask,
        changeUserPermission.file,
        changeUserPermission.delete,
        changeUserPermission.anonymous
    )
    call.respond(HttpStatus.OK)
}

private suspend fun Context.getUserPermission()
{
    // 获取登录用户，若未登录返回未授权
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    // 获取目标用户id，若未提供返回不合法的请求
    val id = call.parameters["id"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    // 获取目标用户，若不存在返回未找到
    val user = UserDatabase.getUser(id) ?: return call.respond(HttpStatus.NotFound)
    // 生成返回结果，若权限不足返回null
    val res = PermissionGroupForOperation(
        id = id,
        read = if (loginUser.read >= PermissionLevel.ADMIN) user.read else null,
        post = if (loginUser.post >= PermissionLevel.ADMIN) user.post else null,
        comment = if (loginUser.comment >= PermissionLevel.ADMIN) user.comment else null,
        ask = if (loginUser.ask >= PermissionLevel.ADMIN) user.ask else null,
        file = if (loginUser.file >= PermissionLevel.ADMIN) user.file else null,
        delete = if (loginUser.delete >= PermissionLevel.ADMIN) user.delete else null,
        anonymous = if (loginUser.anonymous >= PermissionLevel.ADMIN) user.anonymous else null,
    )
    call.respond(res)
}