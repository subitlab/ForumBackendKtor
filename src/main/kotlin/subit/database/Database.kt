package subit.database

import io.ktor.server.application.*
import subit.console.AnsiStyle.Companion.RESET
import subit.console.SimpleAnsiColor.Companion.CYAN
import subit.console.SimpleAnsiColor.Companion.GREEN
import subit.console.SimpleAnsiColor.Companion.RED
import subit.database.sqlImpl.SqlDatabaseImpl
import subit.logger.ForumLogger
import subit.utils.ForumThreadGroup.shutdown
import java.util.*
import kotlin.reflect.KClass
import kotlin.system.exitProcess

val databaseImpls: List<IDatabase> = listOf(
    SqlDatabaseImpl
)

fun Application.loadDatabaseImpl()
{
    val impls = databaseImpls.associateBy { it.name }
    ForumLogger.config("Available database implementations: ${impls.keys.joinToString(", ")}")

    val databaseImpl = environment.config.propertyOrNull("database.impl")?.getString()?.lowercase()

    if (databaseImpl == null)
    {
        val implNames = impls.keys.joinToString(", ")
        ForumLogger.severe("${RED}Database implementation not found")
        ForumLogger.severe("${RED}Please add properties in application.conf: ${CYAN}database.impl ${GREEN}(options: $implNames)${RESET}")
        shutdown(1, "Database implementation not found")
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
    ForumLogger.severe("${RED}Database implementation not found: $GREEN$databaseImpl")
    ForumLogger.severe("${RED}Available implementations: $GREEN${impls.keys.joinToString(", ")}")
    shutdown(1, "Database implementation not found")
}

interface IDatabase
{
    val name: String
    fun Application.init()
}