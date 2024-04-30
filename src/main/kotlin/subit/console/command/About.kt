package subit.console.command

import subit.version

/**
 * About command.
 * print some info about this server.
 */
object About: Command
{
    override val description = "Show about."
    override val aliases = listOf("version", "ver")

    override fun execute(args: List<String>): Boolean
    {
        CommandSet.out.println("SubIT Forum Backend")
        CommandSet.out.println("Version: $version")
        CommandSet.out.println("Author: SubIT Team")
        CommandSet.out.println("Github: https://github.com/subitlab")
        CommandSet.out.println("Website: https://subit.org.cn")
        CommandSet.out.println("Email: subit@i.pkuschool.edu.cn")
        return true
    }
}