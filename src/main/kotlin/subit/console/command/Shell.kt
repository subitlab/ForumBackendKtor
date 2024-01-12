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
        val pb=ProcessBuilder()
        pb.command(args)
        val process=pb.start()
        process.inputStream.transferTo(CommandSet.out)
        process.errorStream.transferTo(CommandSet.err)
        process.waitFor()
        return true
    }
}