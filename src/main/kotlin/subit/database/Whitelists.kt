package subit.database

interface Whitelists
{
    suspend fun add(email: String)
    suspend fun remove(email: String)
    suspend fun isWhitelisted(email: String): Boolean
    suspend fun getWhitelist(): List<String>
}