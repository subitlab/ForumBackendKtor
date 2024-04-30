package subit.config

import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Comment

@Serializable
data class FilesConfig(
    @Comment("用户上传文件最大大小")
    val userMaxFileSize: Long,
    @Comment("管理员上传文件最大大小")
    val adminMaxFileSize: Long,
)

var filesConfig: FilesConfig by config("files.yml", FilesConfig(1L shl 30, Long.MAX_VALUE))