package subit.database.memoryImpl

import org.koin.core.component.KoinComponent
import subit.dataClasses.BlockId
import subit.dataClasses.PermissionLevel
import subit.dataClasses.UserId
import subit.database.Permissions
import java.util.*

class PermissionsImpl: Permissions, KoinComponent
{
    private val permissions = Collections.synchronizedMap(hashMapOf<Long, PermissionLevel>())

    private infix fun UserId.of(bid: BlockId) = (bid.value.toLong() shl 32) or value.toLong()

    override suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionLevel)
    {
        permissions[uid of bid] = permission
    }

    override suspend fun getPermission(block: BlockId, user: UserId): PermissionLevel =
        permissions[user of block] ?: PermissionLevel.NORMAL
}