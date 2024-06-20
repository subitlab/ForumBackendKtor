package subit.console.command

import subit.console.Console

/**
 * 清屏命令
 */
object Clear: Command
{
    override val description = "Clear screen"
    override val log = false
    override suspend fun execute(args: List<String>): Boolean
    {
        Console.clear()
        return true
    }
}