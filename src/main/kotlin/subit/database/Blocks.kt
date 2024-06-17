package subit.database

import subit.dataClasses.*

interface Blocks
{
    suspend fun createBlock(
        name: String,
        description: String,
        parent: BlockId?,
        creator: UserId,
        postingPermission: PermissionLevel = PermissionLevel.NORMAL,
        commentingPermission: PermissionLevel = PermissionLevel.NORMAL,
        readingPermission: PermissionLevel = PermissionLevel.NORMAL,
        anonymousPermission: PermissionLevel = PermissionLevel.NORMAL
    ): BlockId

    suspend fun setPermission(
        block: BlockId,
        posting: PermissionLevel?,
        commenting: PermissionLevel?,
        reading: PermissionLevel?,
        anonymous: PermissionLevel?
    )

    suspend fun getBlock(block: BlockId): BlockFull?
    suspend fun setState(block: BlockId, state: State)
    suspend fun getChildren(loginUser: UserId?, parent: BlockId?, begin: Long, count: Int): Slice<BlockId>
    suspend fun searchBlock(loginUser: UserId?, key: String, begin: Long, count: Int): Slice<BlockId>
}