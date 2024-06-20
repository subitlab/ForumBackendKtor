package subit.console.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 使用shell命令
 */
object Shell: Command
{
    override val description = "Use shell commands."
    override val args = "[command]"
    override val aliases = listOf("sh")

    override suspend fun execute(args: List<String>): Boolean = withContext(Dispatchers.IO)
    {
        ProcessBuilder().command(args).inheritIO().start().waitFor()
        true
    }
}