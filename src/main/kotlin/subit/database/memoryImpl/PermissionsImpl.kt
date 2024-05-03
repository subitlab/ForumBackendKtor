package subit.database.memoryImpl

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import subit.dataClasses.BlockId
import subit.dataClasses.PermissionLevel
import subit.dataClasses.UserId
import subit.database.Blocks
import subit.database.Permissions
import subit.database.Users
import java.util.Collections

class PermissionsImpl: Permissions, KoinComponent
{
    private val permissions = Collections.synchronizedMap(mutableMapOf<Pair<BlockId, UserId>, PermissionLevel>())

    private val users: Users by inject()
    private val blocks: Blocks by inject()

    override suspend fun setPermission(bid: BlockId, uid: UserId, permission: PermissionLevel)
    {
        permissions[bid to uid] = permission
    }
    private fun getRawPermission(bid: BlockId, user: UserId) = permissions[bid to user] ?: PermissionLevel.NORMAL
    override suspend fun getPermission(bid0: BlockId, user: UserId): PermissionLevel
    {
        var bid = bid0
        var permission = getRawPermission(bid, user)
        val userFull = users.getUser(user) ?: return PermissionLevel.NORMAL
        while (true)
        {
            bid = blocks.getBlock(bid)?.parent ?: break
            permission = PermissionLevel.getEffectivePermission(permission, getRawPermission(bid, user))
        }
        return maxOf(permission,userFull.permission)
    }
}