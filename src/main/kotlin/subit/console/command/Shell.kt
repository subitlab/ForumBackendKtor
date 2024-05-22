package subit.console.command

/**
 * 使用shell命令
 */
object Shell: Command
{
    override val description = "Use shell commands."
    override val args = "[command]"
    override val aliases = listOf("sh")

    override fun execute(args: List<String>): Boolean
    {
        ProcessBuilder().command(args).inheritIO().start().waitFor()
        return true
    }
}