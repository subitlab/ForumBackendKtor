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
import subit.dataClasses.*
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.UserId.Companion.toUserIdOrNull
import subit.database.Operations
import subit.database.Users
import subit.database.addOperation
import subit.database.receiveAndCheckBody
import subit.router.Context
import subit.router.authenticated
import subit.router.get
import subit.router.paged
import subit.utils.*
import subit.utils.FileUtils.canDelete
import subit.utils.FileUtils.canGet
import subit.utils.FileUtils.getSpaceInfo
import subit.utils.FileUtils.getUserFiles
import java.io.InputStream
import java.util.*

fun Route.files() = route("files", {
    tags = listOf("文件")
})
{
    get("/{id}/{type}", {
        description =
            "获取文件, 若是管理员可以获取任意文件, 否则只能获取自己上传的文件. 注意若文件过期则只能获取info"
        request {
            authenticated(false)
            pathParameter<String>("id")
            {
                required = true
                description = "文件ID"
                example = UUID.randomUUID().toString()
            }
            pathParameter<GetFileType>("type")
            {
                required = true
                description = "获取类型, 可以获取文件信息或文件的数据"
                example = GetFileType.INFO
            }
        }
        response {
            "200: 获取文件信息" to {
                description = "当type为INFO时返回文件信息"
                body<FileUtils.FileInfo>()
                {
                    example("example", FileUtils.FileInfo("fileName", UserId(0), true, 0, "md5"))
                }
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
            pathParameter<String>("id")
            {
                required = true
                description = "文件ID"
                example = UUID.randomUUID().toString()
            }
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
            multipartBody()
            {
                required = true
                description = "第一部分是文件信息, 第二部分是文件数据"
                part<UploadFile>("info")
                part<Any>("file")
                {
                    mediaTypes = listOf(ContentType.Application.OctetStream)
                }
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
            pathParameter<RawUserId>("id")
            {
                required = true
                description = "用户ID, 为0表示当前登陆的用户"
            }
            paged()
        }
        response {
            statuses<Files>(
                HttpStatus.OK,
                example = Files(FileUtils.SpaceInfo(0L, 0L, 0), sliceOf(UUID.randomUUID().toString()))
            )
        }
    }) { getFileList() }

    post("changePublic", {
        description = "修改文件的公开状态, 只能修改自己上传的文件"
        request {
            authenticated(true)
            body<ChangePublic>
            {
                required = true
                description = "文件信息"
                example("example", ChangePublic(UUID.randomUUID().toString(), true))
            }
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
            body<ChangePermission>
            {
                required = true
                description = "文件信息"
                example("example", ChangePermission(UserId(0), PermissionLevel.NORMAL))
            }
        }
        response {
            statuses(HttpStatus.OK)
            statuses(HttpStatus.NotFound)
            statuses(HttpStatus.Forbidden)
        }
    }) { changePermission() }
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
    val fileInfo = FileUtils.getFileInfo(id) ?: return call.respond(HttpStatus.NotFound)
    val user = getLoginUser()
    if (!user.canGet(fileInfo)) return call.respond(HttpStatus.Forbidden)
    val type = call.parameters["type"] ?: return call.respond(HttpStatus.BadRequest)
    return when
    {
        type.equals(GetFileType.INFO.name, true) -> call.respond(HttpStatus.OK, fileInfo)
        type.equals(GetFileType.DATA.name, true) ->
        {
            val file = FileUtils.getFile(id, fileInfo) ?: return call.respond(HttpStatus.NotFound)
            call.response.header("Content-Disposition", "attachment; filename=\"${fileInfo.fileName}\"")
            call.response.header("Content-md5", fileInfo.md5)
            call.respondFile(file)
        }

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
        when (part.name)
        {
            "info" ->
            {
                part as PartData.FormItem
                fileInfo = FileUtils.fileInfoSerializer.decodeFromString(part.value)
            }

            "file" ->
            {
                part as PartData.FileItem
                size = part.headers["Content-Length"]?.toLongOrNull()
                input = part.streamProvider()
            }

            else   -> Unit
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
    call.respond(HttpStatus.OK)
}

@Serializable
private data class Files(val info: FileUtils.SpaceInfo, val list: Slice<String>)

private suspend fun Context.getFileList()
{
    val id = call.parameters["id"]?.toUserIdOrNull() ?: return call.respond(HttpStatus.BadRequest)
    val begin = call.parameters["begin"]?.toLongOrNull() ?: 0
    val count = call.parameters["count"]?.toIntOrNull() ?: 10
    val user = getLoginUser()
    if (user != null && (user.id == id || id == UserId(0) || user.permission >= PermissionLevel.ADMIN))
    {
        val files = user.id.getUserFiles().map { it.first.toString() }
        val info = user.getSpaceInfo()
        return call.respond(HttpStatus.OK, Files(info, files.asSlice(begin, count)))
    }
    val file = id.getUserFiles().filter { user.canGet(it.second) }.map { it.first.toString() }
    val info = get<Users>().getUser(id)?.getSpaceInfo() ?: return call.respond(HttpStatus.NotFound)
    call.respond(HttpStatus.OK, Files(info, file.asSlice(begin, count)))
}

@Serializable
private data class ChangePublic(val id: String, val public: Boolean)

private suspend fun Context.changePublic()
{
    val (id, public) = receiveAndCheckBody<ChangePublic>().let {
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
    val changePermission = receiveAndCheckBody<ChangePermission>()
    val user = get<Users>().getUser(changePermission.id) ?: return call.respond(HttpStatus.NotFound)
    if (loginUser.permission < PermissionLevel.ADMIN || loginUser.permission <= user.permission)
        return call.respond(HttpStatus.Forbidden)
    get<Users>().changePermission(changePermission.id, changePermission.permission)
    get<Operations>().addOperation(loginUser.id, changePermission)
    call.respond(HttpStatus.OK)
}