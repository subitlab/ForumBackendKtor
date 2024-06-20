package subit.console.command

import subit.utils.Power

/**
 * 杀死服务器
 */
object Stop: Command
{
    override val description = "Stop the server."
    override suspend fun execute(args: List<String>): Boolean =
        Power.shutdown(0, "stop command executed.")
}