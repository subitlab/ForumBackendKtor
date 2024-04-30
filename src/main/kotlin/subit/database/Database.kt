package subit.database

import io.ktor.server.application.*
import subit.console.AnsiStyle.Companion.RESET
import subit.console.SimpleAnsiColor.Companion.CYAN
import subit.console.SimpleAnsiColor.Companion.GREEN
import subit.database.sqlImpl.SqlDatabaseImpl
import subit.logger.ForumLogger
import kotlin.system.exitProcess

const val DEFAULT_DATABASE_IMPL = "sql"
val impls: Map<String, IDatabase> = mapOf(
    "sql" to SqlDatabaseImpl,
)

fun Application.loadDatabaseImpl()
{
    val databaseImpl = environment.config.propertyOrNull("database.impl")?.getString()?.lowercase()

    if (databaseImpl == null)
    {
        val implNames = impls.keys.joinToString(", ")
        ForumLogger.warning("Database implementation not set, using default implementation: $DEFAULT_DATABASE_IMPL")
        ForumLogger.warning("If you want to use another implementation, please set ${CYAN}database.impl${GREEN} (options: $implNames)${RESET}")
    }
    val impl = impls[databaseImpl]
    if (impl != null)
    {
        ForumLogger.info("Using database implementation: $databaseImpl")
        impl.apply {
            init()
        }
        return
    }
    val defaultImpl = impls[DEFAULT_DATABASE_IMPL]
    if (defaultImpl != null)
    {
        if (databaseImpl != null)
        {
            ForumLogger.warning(
                "Unknown database implementation: $databaseImpl, using default implementation: $DEFAULT_DATABASE_IMPL"
            )
        }
        defaultImpl.apply {
            init()
        }
        return
    }
    ForumLogger.severe("No database implementation found")
    exitProcess(1)
}

interface IDatabase
{
    fun Application.init()
}