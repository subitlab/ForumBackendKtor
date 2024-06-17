package subit.database.memoryImpl

import org.koin.core.component.KoinComponent
import subit.dataClasses.BlockId
import subit.dataClasses.BlockUserId
import subit.dataClasses.PermissionLevel
import subit.dataClasses.UserId
import subit.database.Permissions
import java.util.*

class PermissionsImpl: Permissions, KoinComponent
{
    private val permissions = Collections.synchronizedMap(hashMapOf<BlockUserId, PermissionLevel>())

    override suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionLevel)
    {
        permissions[BlockUserId(uid, bid)] = permission
    }

    override suspend fun getPermission(block: BlockId, user: UserId): PermissionLevel =
        permissions[BlockUserId(user, block)] ?: PermissionLevel.NORMAL
}