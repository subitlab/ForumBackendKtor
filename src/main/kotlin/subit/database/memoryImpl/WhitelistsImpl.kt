package subit.database.memoryImpl

import subit.database.Whitelists
import java.util.Collections

class WhitelistsImpl: Whitelists
{
    private val set = Collections.synchronizedSet(mutableSetOf<String>())

    override suspend fun add(email: String)
    {
        set.add(email)
    }
    override suspend fun remove(email: String)
    {
        set.remove(email)
    }
    override suspend fun isWhitelisted(email: String): Boolean = set.contains(email)
    override suspend fun getWhitelist(): List<String> = set.toList()
}