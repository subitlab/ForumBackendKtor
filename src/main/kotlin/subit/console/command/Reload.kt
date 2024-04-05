package subit.console.command

import subit.Loader

/**
 * Reload configs.
 */
object Reload: Command
{
    override val description = "Reload configs."

    override fun execute(args: List<String>): Boolean
    {
        Loader()
        CommandSet.out.println("Reloaded.")
        return true
    }
}