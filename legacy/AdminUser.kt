package subit.router

/*
fun Route.adminUser()
{
    route("/admin/user", {
        tags = listOf("用户管理")
        description = "用户管理接口"
        request {
            authenticated(true)
        }
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
            description = "禁言用户, 需要当前用户的user权限大于ADMIN且大于对方的user权限"
            request {
                body<ProhibitUser> { description = "封禁信息, 其中time是封禁结束的时间戳" }
            }
            response {
                addHttpStatuses(HttpStatus.OK)
            }
        }) { prohibitUser() }

        post("permission/{id}", {
            description = "修改用户权限, 需要管理员权限。若试图将目标的权限修改到超过自己的权限，返回禁止访问"
            request {
                pathParameter<Long>("id") { description = "用户ID" }
                body<PermissionGroupForOperation> { description = "修改权限信息" }
            }
            response {
                addHttpStatuses(HttpStatus.OK, HttpStatus.Forbidden)
            }
        }) { changeUserPermission() }

        get("permission/{id}", {
            description = "获取用户权限, 若user权限达到ADMIN可以获取所有人的, 否则只能获取自己的"
            request {
                pathParameter<Long>("id") { description = "用户ID" }
            }
            response {
                addHttpStatuses<PermissionGroupForOperation>(HttpStatus.OK)
                addHttpStatuses(
                    HttpStatus.Unauthorized,
                    HttpStatus.BadRequest,
                    HttpStatus.NotFound,
                    HttpStatus.Forbidden
                )
            }
        }) { getUserPermission() }
    }
}



private suspend fun Context.changeUserPermission()
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    // 考虑到修改权限的安全性问题, 为防止权限错误修改, 若id不合法直接返回不合法的请求
    val id = call.parameters["id"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val changeUserPermission = call.receive<PermissionGroupForOperation>()
    // 若试图将目标的权限修改到超过自己的权限，返回禁止访问
    if (!loginUser.permissions.canChange(changeUserPermission)) return call.respond(HttpStatus.Forbidden)
    // 获取目标用户, 若不存在返回未找到
    val user = UserDatabase.getUser(id) ?: return call.respond(HttpStatus.NotFound)
    // 若试图修改的用户权限高于自己的权限，返回禁止访问
    if (user.id != loginUser.id && !loginUser.permissions.canChange(user.toPermissionGroupForOperation()))
        return call.respond(HttpStatus.Forbidden)
    // 修改用户权限
    UserDatabase.changeUserPermission(id, changeUserPermission)
    AdminOperationDatabase.addOperation(loginUser.id, changeUserPermission)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.getUserPermission()
{
    // 获取登录用户，若未登录返回未授权
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    // 获取目标用户id, 因为仅查询权限, 不会导致权限错误修改, 所以若id不合法视作查询自己的权限
    val id = call.parameters["id"]?.toLongOrNull() ?: 0L
    // 若询问自己的权限，直接返回
    if (id == 0L) return call.respond(HttpStatus.OK, loginUser.toPermissionGroupForOperation())
    // 若用户管理权限不足ADMIN，返回禁止访问
    if (loginUser.permissions.user < PermissionLevel.ADMIN) return call.respond(HttpStatus.Forbidden)
    // 获取目标用户，若不存在返回未找到
    val user = UserDatabase.getUser(id) ?: return call.respond(HttpStatus.NotFound)
    call.respond(user.toPermissionGroupForOperation())
}
 */