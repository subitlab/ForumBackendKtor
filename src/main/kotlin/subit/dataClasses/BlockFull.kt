package subit.dataClasses

import kotlinx.serialization.Serializable

/**
 * 板块信息
 * @property id 板块ID
 * @property name 板块名称
 * @property description 板块描述
 * @property parent 父板块ID
 * @property creator 创建者ID
 * @property posting 发帖权限
 * @property commenting 评论权限
 * @property reading 阅读权限
 */
@Serializable
data class BlockFull(
    val id: BlockId,
    val name: String,
    val description: String,
    val parent: BlockId?,
    val creator: UserId,
    val posting: PermissionLevel,
    val commenting: PermissionLevel,
    val reading: PermissionLevel,
    val anonymous: PermissionLevel,
)
