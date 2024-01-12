package subit.console.command

import kotlin.system.exitProcess

/**
 * 杀死服务器
 */
object Stop: Command
{
    override val description = "Stop the server."

    override fun execute(args: List<String>): Boolean = exitProcess(0)
}