package subit.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import subit.config.filesConfig
import subit.dataClasses.PermissionLevel
import subit.dataClasses.UserFull
import subit.dataClasses.UserId
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.*
import javax.imageio.ImageIO

/**
 * 文件工具类
 * 文件存储结构
 * file/
 *   index/ ${id}.index #文件的info
 *   raw/
 *     ${user}/
 *       user.index
 *       ${id}.file
 *
 * 当通过id查找文件时, 先在通过 "index/id.index" 查找到文件信息, 然后通过 "raw/${user}/${id}.file" 获取文件内容
 */
object FileUtils
{
    val dataFolder = File("data")
    private val fileFolder = File(dataFolder, "files")
    private val indexFolder = File(fileFolder, "index")
    private val rawFolder = File(fileFolder, "raw")

    fun init()
    {
        dataFolder.mkdirs()
        fileFolder.mkdirs()
        indexFolder.mkdirs()
        rawFolder.mkdirs()

    }

    val fileInfoSerializer = Json()
    {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class FileInfo(
        val fileName: String,
        val user: UserId,
        val public: Boolean,
        val size: Long,
        val md5: String
    )
    {
        val private: Boolean get() = !public
    }

    fun UserFull?.canGet(file: FileInfo) =
        file.public || this?.id == file.user || (this != null && this.filePermission >= PermissionLevel.ADMIN)

    fun UserFull?.canDelete(file: FileInfo) =
        this != null && (this.id == file.user || this.filePermission >= PermissionLevel.ADMIN)

    private fun getFileInfo(file: File): FileInfo? = runCatching {
        fileInfoSerializer.decodeFromString(FileInfo.serializer(), file.readText())
    }.getOrNull()

    private fun getRandomId(): UUID
    {
        var id = UUID.randomUUID()
        while (File(indexFolder, "${id}.index").exists()) id = UUID.randomUUID()
        return id
    }

    private suspend fun getFileMd5(file: File): String
    {
        val md = MessageDigest.getInstance("MD5") // 获取MD5实例
        val buffer = ByteArray(8192) // 缓冲区
        withContext(Dispatchers.IO) // 使用IO线程
        {
            FileInputStream(file).use()
            { inputStream ->
                var read: Int
                while (inputStream.read(buffer).also { read = it } > 0) md.update(buffer, 0, read)
            }
        }
        val byteArray = md.digest() // 获取MD5值
        return byteArray.joinToString("") { "%02x".format(it) } // 转为16进制字符串
    }

    /**
     * 保存一个文件
     * @param input 文件输入流
     * @param fileName 文件名
     * @param user 所属用户
     * @param public 是否公开
     * @return 文件id
     */
    suspend fun saveFile(input: InputStream, fileName: String, user: UserId, public: Boolean): UUID
    {
        val id = getRandomId()
        val userFile = File(rawFolder, user.toString(16))
        userFile.mkdirs()
        val rawFile = File(userFile, "${id}.file")
        val indexFile = File(indexFolder, "${id}.index")
        withContext(Dispatchers.IO) { rawFile.createNewFile() }
        rawFile.outputStream().use { input.copyTo(it) }
        val md5 = getFileMd5(rawFile)
        val fileInfo = FileInfo(fileName, user, public, rawFile.length(), md5)
        fileInfoSerializer.encodeToString(FileInfo.serializer(), fileInfo).let(indexFile::writeText)
        return id
    }

    /**
     * 获取一个文件的信息
     * @param id 文件id
     */
    fun getFileInfo(id: UUID): FileInfo?
    {
        val indexFile = File(indexFolder, "${id}.index")
        return if (indexFile.exists()) getFileInfo(indexFile)
        else null
    }

    suspend fun UserId.getUserFiles(): Sequence<Pair<UUID, FileInfo>> = withContext(Dispatchers.IO)
    {
        val userFolder = File(rawFolder, this@getUserFiles.toString(16))
        if (!userFolder.exists()) return@withContext emptySequence()
        userFolder.walk()
            .filter { it.isFile }
            .mapNotNull { it.nameWithoutExtension.toUUIDOrNull() }
            .mapNotNull { id -> getFileInfo(id)?.let { id to it } }
    }

    fun getFile(id: UUID, info: FileInfo): File?
    {
        val userFolder = File(rawFolder, info.user.toString(16))
        val rawFile = File(userFolder, "${id}.file")
        return if (rawFile.exists()) rawFile else null
    }

    @Serializable
    data class SpaceInfo(val max: Long, val used: Long, val fileCount: Int)
    {
        fun canUpload(size: Long) = max - used >= size
    }
    /**
     * 获取使用空间与剩余空间
     */
    suspend fun UserFull.getSpaceInfo(): SpaceInfo = withContext(Dispatchers.IO)
    {
        val userFolder = File(rawFolder, this@getSpaceInfo.id.toString(16))
        val max = if (this@getSpaceInfo.filePermission >= PermissionLevel.ADMIN) filesConfig.adminMaxFileSize
        else filesConfig.userMaxFileSize
        val (used, count) = userFolder.walk().filter { it.isFile }.fold(0L to 0)
        { (size, count), file ->
            size + file.length() to count + 1
        }
        SpaceInfo(max, used, count)
    }

    /**
     * 删除文件, 仅删除raw文件索引不删除
     */
    fun deleteFile(id: UUID)
    {
        val info = getFileInfo(id) ?: return
        deleteFile(id, info)
    }

    private fun deleteFile(id: UUID, info: FileInfo) = deleteFile(id, info.user)
    private fun deleteFile(id: UUID, user: UserId)
    {
        File(rawFolder, "${user}.${id}.file").delete()
    }

    fun changeInfo(id: UUID, info: FileInfo)
    {
        val indexFile = File(indexFolder, "${id}.index")
        indexFile.writeText(fileInfoSerializer.encodeToString(FileInfo.serializer(), info))
    }
}

/**
 * 头像工具类
 * 头像存储在本地, 按照用户ID给每个用户创建一个文件夹, 文件夹中存放用户的头像
 * 头像文件名为数字, 从0开始, 依次递增, 数字最大的即为当前使用的头像
 * 默认头像存放在 default 文件夹中, 可以在其中添加任意数量的头像, 用户被设置为默认头像时, 会随机选择一个头像
 */
object AvatarUtils
{
    private val avatarFolder = File(FileUtils.dataFolder, "/avatars")

    init
    {
        avatarFolder.mkdirs()
    }

    fun setAvatar(user: UserId, avatar: BufferedImage)
    {
        val userAvatarFolder = File(avatarFolder, user.toString(16).padStart(16, '0'))
        userAvatarFolder.mkdirs()
        // 文件夹中已有的头像数量
        val avatarCount = userAvatarFolder.listFiles()?.size ?: 0
        val avatarFile = File(userAvatarFolder, "${avatarCount}.png")
        avatarFile.createNewFile()
        // 将头像大小调整为 1024x1024
        val resizedAvatar = BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB)
        val graphics = resizedAvatar.createGraphics()
        graphics.drawImage(avatar, 0, 0, 1024, 1024, null)
        graphics.dispose()
        // 保存头像
        ImageIO.write(resizedAvatar, "png", avatarFile)
    }

    fun setDefaultAvatar(user: UserId): BufferedImage
    {
        val userAvatarFolder = File(avatarFolder, user.toString(16).padStart(16, '0'))
        userAvatarFolder.mkdirs()
        // 文件夹中已有的头像数量
        val avatarCount = userAvatarFolder.listFiles()?.size ?: 0
        val avatarFile = File(userAvatarFolder, "${avatarCount}.png")
        avatarFile.createNewFile()
        // 在默认头像文件夹中随机选择一个头像
        val defaultAvatarFolder = File(avatarFolder, "default")
        val defaultAvatarFiles = defaultAvatarFolder.listFiles()
        val defaultAvatar = defaultAvatarFiles?.randomOrNull()
        // 保存头像
        defaultAvatar?.copyTo(avatarFile)
        return ImageIO.read(defaultAvatar)
    }

    fun getAvatar(user: UserId): BufferedImage
    {
        val userAvatarFolder = File(avatarFolder, user.toString(16).padStart(16, '0'))
        val avatarCount = userAvatarFolder.listFiles()?.size ?: 0
        val avatarFile = File(userAvatarFolder, "${avatarCount-1}.png")
        return if (avatarFile.exists()) ImageIO.read(avatarFile) else setDefaultAvatar(user)
    }
}