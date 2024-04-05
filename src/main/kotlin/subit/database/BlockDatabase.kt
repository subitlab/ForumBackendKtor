package subit.database

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update

/**
 * 板块数据库交互类
 */
object BlockDatabase: DataAccessObject<BlockDatabase.Blocks>(Blocks)
{
    object Blocks: IdTable<Int>("blocks")
    {
        /**
         * 板块ID
         */
        override val id: Column<EntityID<Int>> = integer("id").autoIncrement().entityId()

        /**
         * 板块名称
         */
        val name = varchar("name", 100).index()

        /**
         * 板块描述
         */
        val description = text("description")

        /**
         * 板块父板块
         */
        val parent = reference("parent", Blocks, ReferenceOption.CASCADE, ReferenceOption.CASCADE).nullable().default(null).index()

        /**
         * 板块创建者
         */
        val creator = reference("creator", UserDatabase.Users).index()

        /**
         * 发帖所需权限
         */
        val postingPermission = enumeration<PermissionLevel>("postingPermission").default(PermissionLevel.NORMAL)

        /**
         * 评论所需权限
         */
        val commentingPermission = enumeration<PermissionLevel>("commentingPermission").default(PermissionLevel.NORMAL)

        /**
         * 阅读所需权限
         */
        val readingPermission = enumeration<PermissionLevel>("readingPermission").default(PermissionLevel.NORMAL)
        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id)
    }

    suspend fun createBlock(
        name: String,
        description: String,
        parent: Int?,
        creator: Long,
        postingPermission: PermissionLevel = PermissionLevel.NORMAL,
        commentingPermission: PermissionLevel = PermissionLevel.NORMAL,
        readingPermission: PermissionLevel = PermissionLevel.NORMAL
    ) = query()
    {
        insert {
            it[Blocks.name] = name
            it[Blocks.description] = description
            it[Blocks.parent] = parent
            it[Blocks.creator] = creator
            it[Blocks.postingPermission] = postingPermission
            it[Blocks.commentingPermission] = commentingPermission
            it[Blocks.readingPermission] = readingPermission
        }
    }

    suspend fun setPostingPermission(block: Int, permission: PermissionLevel) = query()
    {
        update({ id eq block })
        {
            it[postingPermission] = permission
        }
    }
}