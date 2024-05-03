package subit.database.memoryImpl

import subit.dataClasses.*
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.Blocks
import java.util.Collections

class BlocksImpl: Blocks
{
    private val map = Collections.synchronizedMap(mutableMapOf<BlockId, BlockFull>())

    override suspend fun createBlock(
        name: String,
        description: String,
        parent: BlockId?,
        creator: UserId,
        postingPermission: PermissionLevel,
        commentingPermission: PermissionLevel,
        readingPermission: PermissionLevel,
        anonymousPermission: PermissionLevel
    ): BlockId
    {
        val id = map.size+1
        map[id] = BlockFull(
            id = id,
            name = name,
            description = description,
            parent = parent,
            creator = creator,
            posting = postingPermission,
            commenting = commentingPermission,
            reading = readingPermission,
            anonymous = anonymousPermission,
            state = State.NORMAL
        )
        return id
    }
    override suspend fun setPermission(
        block: BlockId,
        posting: PermissionLevel?,
        commenting: PermissionLevel?,
        reading: PermissionLevel?,
        anonymous: PermissionLevel?
    )
    {
        val b = map[block] ?: return
        map[block] = b.copy(
            posting = posting ?: b.posting,
            commenting = commenting ?: b.commenting,
            reading = reading ?: b.reading,
            anonymous = anonymous ?: b.anonymous
        )
    }
    override suspend fun getBlock(block: BlockId): BlockFull?
    {
        return map[block]
    }
    override suspend fun setState(block: BlockId, state: State)
    {
        val b = map[block] ?: return
        map[block] = b.copy(state = state)
    }
    override suspend fun getChildren(parent: BlockId): List<BlockFull>
    {
        return map.values.filter { it.parent == parent }
    }
    override suspend fun searchBlock(user: UserId?, key: String, begin: Long, count: Int): Slice<BlockFull>
    {
        return map.values.filter { it.name.contains(key) }.asSlice(begin, count)
    }
}