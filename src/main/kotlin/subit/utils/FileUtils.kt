package subit.utils

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object FileUtils
{
    val dataFolder = File("data")
}

object AvatarUtils
{
    val avatarFolder = File(FileUtils.dataFolder,"/avatars")

    fun setAvatar(user: ULong, avatar: BufferedImage)
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

    fun setDefaultAvatar(user: ULong): BufferedImage
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

    fun getAvatar(user: ULong): BufferedImage
    {
        val userAvatarFolder = File(avatarFolder, user.toString(16).padStart(16, '0'))
        val avatarCount = userAvatarFolder.listFiles()?.size ?: 0
        val avatarFile = File(userAvatarFolder, "${avatarCount - 1}.png")
        return if (avatarFile.exists()) ImageIO.read(avatarFile) else setDefaultAvatar(user)
    }
}