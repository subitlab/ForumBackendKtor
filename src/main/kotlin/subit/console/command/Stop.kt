package subit.console.command

import subit.utils.ForumThreadGroup

/**
 * 杀死服务器
 */
object Stop: Command
{
    override val description = "Stop the server."
    override fun execute(args: List<String>): Boolean = ForumThreadGroup.shutdown(0, "stop command executed.")
}