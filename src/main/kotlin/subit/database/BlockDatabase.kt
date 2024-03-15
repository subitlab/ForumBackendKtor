package subit.database

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption

/**
 * 板块数据库交互类
 */
object BlockDatabase: DatabaseController<BlockDatabase.Blocks>(Blocks)
{
    object Blocks: IdTable<UInt>("blocks")
    {
        /**
         * 板块ID
         */
        override val id: Column<EntityID<UInt>> = uinteger("id").entityId()

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
        val postingPermission = enumeration<Permission>("postingPermission").default(Permission.NORMAL)

        /**
         * 评论所需权限
         */
        val commentingPermission = enumeration<Permission>("commentingPermission").default(Permission.NORMAL)

        /**
         * 阅读所需权限
         */
        val readingPermission = enumeration<Permission>("readingPermission").default(Permission.NORMAL)

        override val primaryKey: PrimaryKey
            get() = PrimaryKey(id)
    }
}