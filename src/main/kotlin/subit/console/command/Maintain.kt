package subit.console.command

import subit.config.systemConfig

/**
 * Maintain the server.
 */
object Maintain: Command
{
    override val description: String = "Maintain the server."
    override val args: String = "[true/false]"

    override fun execute(args: List<String>): Boolean
    {
        if (args.size > 1) return false
        if (args.isEmpty()) CommandSet.out.println("System Maintaining: ${systemConfig.systemMaintaining}")
        else
        {
            systemConfig = args[0].toBooleanStrictOrNull()?.let { systemConfig.copy(systemMaintaining = it) } ?: return false
        }
        return true
    }
}