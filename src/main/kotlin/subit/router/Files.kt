@file:Suppress("PackageDirectoryMismatch")
package subit.router.files

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.JWTAuth.getLoginUser
import subit.dataClasses.PermissionLevel
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserId
import subit.dataClasses.toUserIdOrNull
import subit.database.AdminOperationDatabase
import subit.database.UserDatabase
import subit.router.Context
import subit.router.authenticated
import subit.utils.*
import subit.utils.FileUtils.canDelete
import subit.utils.FileUtils.canGet
import subit.utils.FileUtils.getSpaceInfo
import subit.utils.FileUtils.getUserFiles
import java.io.InputStream

fun Route.files()
{
    route("files", {
        tags = listOf("文件")
    })
    {
        get("/{id}/{type}", {
            description = "获取文件, 若是管理员可以获取任意文件, 否则只能获取自己上传的文件. 注意若文件过期则只能获取info"
            request {
                authenticated(false)
                pathParameter<String>("id") { required = true; description = "文件ID" }
                pathParameter<GetFileType>("type") { required = true; description = "获取类型, 可以获取文件信息或文件的数据" }
            }
            response {
                "200: 获取文件信息" to {
                    description = "当type为INFO时返回文件信息"
                    body<FileUtils.FileInfo>()
                }
                "200: 获取文件数据" to {
                    description = "当type为DATA时返回文件数据"
                    body { mediaType(ContentType.Application.OctetStream) }
                }
                statuses(HttpStatus.NotFound)
            }
        }) { getFile() }

        delete("/{id}", {
            description = "删除文件, 除管理员外只能删除自己上传的文件"
            request {
                authenticated(true)
                pathParameter<String>("id") { required = true; description = "文件ID" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.NotFound)
                statuses(HttpStatus.Forbidden)
            }
        }) { deleteFile() }

        post("/new", {
            description = "上传文件"
            request {
                authenticated(true)
                body {
                    required = true
                    description = "第一部分是文件信息, 第二部分是文件数据"
                    mediaType(ContentType.MultiPart.FormData)
                }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.BadRequest)
            }
        }) { uploadFile() }

        get("/list/{id}", {
            description = "获取用户上传的文件的列表, 若不是管理员只能获取目标用户公开的文件"
            request {
                authenticated(false)
                pathParameter<UserId>("id") { required = true; description = "用户ID, 为0表示当前登陆的用户" }
                queryParameter<Long>("begin") { required = true; description = "起始位置" }
                queryParameter<Int>("count") { required = true; description = "获取数量" }
            }
            response {
                statuses<Files>(HttpStatus.OK)
            }
        }) { getFileList() }

        post("changePublic", {
            description = "修改文件的公开状态, 只能修改自己上传的文件"
            request {
                authenticated(true)
                body<ChangePublic> { required = true; description = "文件信息" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.NotFound)
                statuses(HttpStatus.Forbidden)
            }
        }) { changePublic() }

        post("changePermission", {
            description = "修改其他用户的文件权限"
            request {
                authenticated(true)
                body<ChangePermission> { required = true; description = "文件信息" }
            }
            response {
                statuses(HttpStatus.OK)
                statuses(HttpStatus.NotFound)
                statuses(HttpStatus.Forbidden)
            }
        }) { changePermission() }
    }
}

@Serializable
private enum class GetFileType
{
    INFO,
    DATA
}

private suspend fun Context.getFile()
{
    val id = call.parameters["id"].toUUIDOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val file = FileUtils.getFileInfo(id) ?: return call.respond(HttpStatus.NotFound)
    val user = getLoginUser()
    if (!user.canGet(file)) return call.respond(HttpStatus.Forbidden)
    val type = call.parameters["type"] ?: return call.respond(HttpStatus.BadRequest)
    return when
    {
        type.equals(GetFileType.INFO.name, true) -> call.respond(file)
        type.equals(GetFileType.DATA.name, true) ->
            FileUtils.getFile(id, file)?.let { call.respondFile(it) } ?: call.respond(HttpStatus.NotFound)

        else                                     -> call.respond(HttpStatus.BadRequest)
    }
}

private suspend fun Context.deleteFile()
{
    val id = call.parameters["id"].toUUIDOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val file = FileUtils.getFileInfo(id) ?: return call.respond(HttpStatus.NotFound)
    if (!getLoginUser().canDelete(file)) return call.respond(HttpStatus.Forbidden)
    FileUtils.deleteFile(id)
    call.respond(HttpStatus.OK)
}

@Serializable
private data class UploadFile(
    val fileName: String,
    val public: Boolean,
)

private suspend fun Context.uploadFile()
{
    val user = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val multipart = call.receiveMultipart()
    var fileInfo: UploadFile? = null
    var input: InputStream? = null
    var size: Long? = null
    multipart.forEachPart { part ->
        when (part)
        {
            is PartData.FormItem -> fileInfo = FileUtils.fileInfoSerializer.decodeFromString(part.value)
            is PartData.FileItem ->
            {
                input = part.streamProvider()
                size = part.headers["Content-Length"]?.toLongOrNull()
            }

            else                 -> Unit
        }
    }
    if (fileInfo == null || input == null) return call.respond(HttpStatus.BadRequest)
    if (size == null || user.getSpaceInfo().canUpload(size!!)) return call.respond(HttpStatus.NotEnoughSpace)
    FileUtils.saveFile(
        input = input!!,
        fileName = fileInfo!!.fileName,
        user = user.id,
        public = fileInfo!!.public
    )
}

@Serializable
private data class Files(val info: FileUtils.SpaceInfo, val list: Slice<String>)

private suspend fun Context.getFileList()
{
    val id = call.parameters["id"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val begin = call.request.queryParameters["begin"]?.toLongOrNull() ?: 0
    val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 10
    val user = getLoginUser()
    if (user != null && (user.id == id || id == 0 || user.permission >= PermissionLevel.ADMIN))
    {
        val files = user.id.getUserFiles().map { it.first.toString() }
        val info = user.getSpaceInfo()
        return call.respond(Files(info, files.asIterable().asSlice(begin, count)))
    }
    val file = id.getUserFiles().filter { user.canGet(it.second) }.map { it.first.toString() }
    val info = UserDatabase.getUser(id)?.getSpaceInfo() ?: return call.respond(HttpStatus.NotFound)
    call.respond(Files(info, file.asIterable().asSlice(begin, count)))
}

@Serializable
private data class ChangePublic(val id: String, val public: Boolean)
private suspend fun Context.changePublic()
{
    val (id, public) = call.receive<ChangePublic>().let {
        val id = it.id.toUUIDOrNull() ?: return@let null
        val public = it.public
        id to public
    } ?: return call.respond(HttpStatus.BadRequest)

    val file = FileUtils.getFileInfo(id) ?: return call.respond(HttpStatus.NotFound)

    if (file.user != getLoginUser()?.id) return call.respond(HttpStatus.Forbidden)
    FileUtils.changeInfo(id, file.copy(public = public))
    call.respond(HttpStatus.OK)
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