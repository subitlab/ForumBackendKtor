package subit.database.memoryImpl

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import subit.dataClasses.BlockId
import subit.dataClasses.BlockUserId
import subit.dataClasses.PermissionLevel
import subit.dataClasses.UserId
import subit.database.Blocks
import subit.database.Permissions
import subit.database.Users
import java.util.*

class PermissionsImpl: Permissions, KoinComponent
{
    private val permissions = Collections.synchronizedMap(hashMapOf<BlockUserId, PermissionLevel>())
    private val users: Users by inject()
    private val blocks: Blocks by inject()
    override suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionLevel)
    {
        permissions[BlockUserId(uid, bid)] = permission
    }

    private fun getRawPermission(bid: BlockId, user: UserId) =
        permissions[BlockUserId(user, bid)] ?: PermissionLevel.NORMAL

    override suspend fun getPermission(block: BlockId, user: UserId): PermissionLevel
    {
        var bid = block
        var permission = getRawPermission(bid, user)
        val userFull = users.getUser(user) ?: return PermissionLevel.NORMAL
        while (true)
        {
            bid = blocks.getBlock(bid)?.parent ?: break
            permission = PermissionLevel.getEffectivePermission(permission, getRawPermission(bid, user))
        }
        return maxOf(permission, userFull.permission)
    }
}