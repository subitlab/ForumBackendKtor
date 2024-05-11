@file:Suppress("PackageDirectoryMismatch")

package subit.router.user

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.*
import subit.database.*
import subit.logger.ForumLogger
import subit.router.Context
import subit.router.authenticated
import subit.router.get
import subit.router.paged
import subit.utils.AvatarUtils
import subit.utils.HttpStatus
import subit.utils.statuses
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun Route.user()
{
    route("/user", {
        tags = listOf("用户")
        description = "用户接口"
    })
    {
        get("/info/{id}", {
            description = """
                获取用户信息, id为0时获取当前登陆用户的信息。
                获取当前登陆用户的信息或当前登陆的用户的user权限不低于ADMIN时可以获取完整用户信息, 否则只能获取基础信息
                """.trimIndent()
            request {
                authenticated(false)
                pathParameter<Long>("id") { required = true; description = "用户ID" }
            }
            response {
                "200: 获取完整用户信息成功" to {
                    description = "当id为0, 即获取当前用户信息或user权限不低于ADMIN时返回"
                    body<UserFull>()
                }
                "200: 获取基础用户的信息成功" to {
                    description = "当id不为0即获取其他用户的信息且user权限低于ADMIN时返回"
                    body<BasicUserInfo>()
                }
                statuses(HttpStatus.NotFound, HttpStatus.Unauthorized)
            }
        }) { getUserInfo() }

        post("/introduce/{id}", {
            description = "修改个人简介, 修改自己的需要user权限在NORMAL以上, 修改他人需要在ADMIN以上"
            request {
                authenticated(true)
                pathParameter<Long>("id")
                {
                    required = true
                    description = """
                        要修改的用户ID, 0为当前登陆用户
                    """.trimIndent()
                }
                body<ChangeIntroduction> { required = true; description = "个人简介" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.NotFound, HttpStatus.Forbidden, HttpStatus.Unauthorized)
            }
        }) { changeIntroduction() }

        post("/avatar/{id}", {
            description = "修改头像, 修改他人头像要求user权限在ADMIN以上"
            request {
                authenticated(true)
                pathParameter<Long>("id")
                {
                    required = true
                    description = """
                        要修改的用户ID, 0为当前登陆用户
                    """.trimIndent()
                }
                body()
                {
                    required = true
                    mediaType(ContentType.Image.Any)
                    description = "头像图片, 要求是正方形的"
                }
            }
            response {
                statuses(
                    HttpStatus.OK,
                    HttpStatus.NotFound,
                    HttpStatus.Forbidden,
                    HttpStatus.Unauthorized,
                    HttpStatus.PayloadTooLarge,
                    HttpStatus.UnsupportedMediaType
                )
            }
        }) { changeAvatar() }

        get("/avatar/{id}", {
            description = "获取头像"
            request {
                authenticated(false)
                pathParameter<Long>("id")
                {
                    required = true
                    description = """
                        要获取的用户ID, 0为当前登陆用户, 若id不为0则无需登陆, 否则需要登陆
                    """.trimIndent()
                }
            }
            response {
                statuses(HttpStatus.BadRequest, HttpStatus.Unauthorized)
                HttpStatus.OK.code to {
                    description = "获取头像成功"
                    body()
                    {
                        description = "获取到的头像, 总是png格式的"
                        mediaType(ContentType.Image.PNG)
                    }
                }
            }
        }) { getAvatar() }

        delete("/avatar/{id}", {
            description = "删除头像, 即恢复默认头像, 删除他人头像要求user权限在ADMIN以上"
            request {
                authenticated(true)
                pathParameter<Long>("id")
                {
                    required = true
                    description = """
                        要删除的用户ID, 0为当前登陆用户
                    """.trimIndent()
                }
            }
            response {
                statuses(
                    HttpStatus.OK,
                    HttpStatus.NotFound,
                    HttpStatus.Forbidden,
                    HttpStatus.Unauthorized,
                )
            }
        }) { deleteAvatar() }

        get("/stars/{id}", {
            description = "获取用户收藏的帖子"
            request {
                authenticated(false)
                pathParameter<Long>("id")
                {
                    required = true
                    description = """
                        要获取的用户ID, 0为当前登陆用户, 若id不为0则无需登陆, 否则需要登陆。
                        若目标用户
                    """.trimIndent()
                }
                paged()
            }
            response {
                statuses(HttpStatus.BadRequest, HttpStatus.Unauthorized)
                statuses<Slice<Long>>(HttpStatus.OK)
            }
        }) { getStars() }

        post("/switchStars", {
            description = "切换是否公开收藏"
            request {
                authenticated(true)
                body<SwitchStars> { required = true; description = "是否公开收藏" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.Unauthorized)
            }
        }) { switchStars() }

        get("/search", {
            description = "搜索用户 会返回所有用户名包含key的用户"
            request {
                queryParameter<String>("key") { required = true;description = "关键字" }
                paged()
            }
            response {
                statuses<Slice<UserId>>(HttpStatus.OK)
            }
        }) { searchUser() }
    }
}

private suspend fun Context.getUserInfo()
{

    val id = call.parameters["id"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.NotFound)
    val loginUser = getLoginUser()
    ForumLogger.config("user=${loginUser?.id} get user info id=$id")
    if (id == 0)
    {
        if (loginUser == null) return call.respond(HttpStatus.Unauthorized)
        call.respond(loginUser)
    }
    else
    {
        val user = get<Users>().getUser(id) ?: return call.respond(HttpStatus.NotFound)
        if (loginUser != null && loginUser.permission >= PermissionLevel.ADMIN)
            call.respond(user)
        else
            call.respond(user.toBasicUserInfo())
    }
}

@Serializable
private data class ChangeIntroduction(val introduction: String)

private suspend fun Context.changeIntroduction()
{
    val id = call.parameters["id"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val changeIntroduction = call.receive<ChangeIntroduction>()
    if (id == 0)
    {
        get<Users>().changeIntroduction(loginUser.id, changeIntroduction.introduction)
        call.respond(HttpStatus.OK)
    }
    else
    {
        checkPermission { checkHasGlobalAdmin() }
        if (get<Users>().changeIntroduction(id, changeIntroduction.introduction))
        {
            get<Operations>().addOperation(loginUser.id, changeIntroduction)
            call.respond(HttpStatus.OK)
        }
        else
            call.respond(HttpStatus.NotFound)
    }
}

private suspend fun Context.changeAvatar()
{
    val id = call.parameters["id"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    // 检查body大小
    val size = call.request.headers["Content-Length"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    // 若图片大于10MB( 10 << 20 ), 返回请求实体过大
    if (size >= 10 shl 20) return call.respond(HttpStatus.PayloadTooLarge)
    val image = runCatching()
                {
                    withContext(Dispatchers.IO)
                    {
                        ImageIO.read(call.receiveStream())
                    }
                }.getOrNull() ?: return call.respond(HttpStatus.UnsupportedMediaType)
    if (id == 0 && loginUser.permission >= PermissionLevel.NORMAL)
    {
        AvatarUtils.setAvatar(loginUser.id, image)
    }
    else
    {
        checkPermission { checkHasGlobalAdmin() }
        val user = get<Users>().getUser(id) ?: return call.respond(HttpStatus.NotFound)
        AvatarUtils.setAvatar(user.id, image)
    }
}

private suspend fun Context.deleteAvatar()
{
    val id = call.parameters["id"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    if (id == 0)
    {
        AvatarUtils.setDefaultAvatar(loginUser.id)
        call.respond(HttpStatus.OK)
    }
    else
    {
        checkPermission { checkHasGlobalAdmin() }
        val user = get<Users>().getUser(id) ?: return call.respond(HttpStatus.NotFound)
        AvatarUtils.setDefaultAvatar(user.id)
        call.respond(HttpStatus.OK)
    }
}

private suspend fun Context.getAvatar()
{
    val id = (call.parameters["id"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)).let {
        if (it == 0) getLoginUser()?.id ?: return call.respond(HttpStatus.Unauthorized)
        else it
    }
    val avatar = AvatarUtils.getAvatar(id)
    call.respondBytes(ContentType.Image.Any, HttpStatusCode.OK)
    {
        val output = ByteArrayOutputStream()
        ImageIO.write(avatar, "png", output)
        output.toByteArray()
    }
}

private suspend fun Context.getStars()
{
    val id = call.parameters["id"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val begin = call.parameters["begin"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val count = call.parameters["count"]?.toIntOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val loginUser = getLoginUser()
    // 若查询自己的收藏
    if (id == 0)
    {
        if (loginUser == null) return call.respond(HttpStatus.Unauthorized)
        val stars = get<Stars>().getStars(user = loginUser.id, begin = begin, limit = count).map { it.post }
        return call.respond(get<Posts>().getPosts(stars))
    }
    // 查询其他用户的收藏
    val user = get<Users>().getUser(id) ?: return call.respond(HttpStatus.NotFound)
    // 若对方不展示收藏, 而当前用户未登录或不是管理员, 返回Forbidden
    if (!user.showStars && (loginUser == null || loginUser.permission < PermissionLevel.ADMIN))
        return call.respond(HttpStatus.Forbidden)
    val stars = get<Stars>().getStars(user = user.id, begin = begin, limit = count).map { it.post }
    call.respond(get<Posts>().getPosts(stars))
}

@Serializable
private data class SwitchStars(val showStars: Boolean)

private suspend fun Context.switchStars()
{
    application
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val switchStars = call.receive<SwitchStars>()
    get<Users>().changeShowStars(loginUser.id, switchStars.showStars)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.searchUser()
{
    val username = call.parameters["key"] ?: return call.respond(HttpStatus.BadRequest)
    val begin = call.parameters["begin"]?.toLongOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val count = call.parameters["count"]?.toIntOrNull() ?: return call.respond(HttpStatus.BadRequest)
    call.respond(get<Users>().searchUser(username, begin, count).map(UserFull::id))
}
