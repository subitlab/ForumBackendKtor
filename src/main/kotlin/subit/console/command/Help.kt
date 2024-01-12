package subit.console.command

import org.jline.reader.Candidate

/**
 * Help command.
 */
object Help: Command
{
    override val description = "Show help."
    override val args = "[command]"
    override val log = false

    /**
     * 根据参数获取命令对象([TreeCommand]), 如果找不到就返回null
     */
    private fun getCommand(args: List<String>): Command?
    {
        var command: Command? = CommandSet
        for (str in args)
        {
            if (command==null) return null
            if (command is TreeCommand) command=command.getCommand(str)
            else return null
        }
        return command
    }

    override fun execute(args: List<String>): Boolean
    {
        if (args.isEmpty()) // 直接使用help命令的话打印所有命令列表
        {
            CommandSet.out.println("Commands:")
            for (command in CommandSet.allCommands()) // 每个命令的格式: 命令名|别名1|别名2 - 命令描述
            {
                val sb: StringBuilder = StringBuilder()
                sb.append("  ${TreeCommand.parseName(command.name)}")
                if (command.aliases.isNotEmpty())
                {
                    sb.append("|")
                    sb.append(command.aliases.joinToString("|"))
                }
                sb.append(" - ${command.description}")
                CommandSet.out.println(sb.toString())
            }
        }
        else
        {
            val command = getCommand(args)
            if (command==null)
            {
                CommandSet.err.println("Unknown command: ${args.dropLast(1).joinToString(" ")}")
                return true
            }
            val rawCmdName=args.dropLast(1).toMutableList() // 实际命令名
            rawCmdName.add(command.name)
            CommandSet.out.println("Command: ${rawCmdName.joinToString(" ")}")
            CommandSet.out.println("Description: ${command.description}")
            CommandSet.out.println("Aliases: ${command.aliases.joinToString(", ")}")
            val cmdArgs=command.args.split("\n")
            if (cmdArgs.size>1)// 如果有多个参数，就分行打印
            {
                CommandSet.out.println("Arguments:")
                cmdArgs.forEach { CommandSet.out.println("  $it") }
            }
            else CommandSet.out.println("Arguments: ${cmdArgs[0]}") // 否则就直接打印
        }
        return true
    }

    override fun tabComplete(args: List<String>): List<Candidate>
    {
        return CommandSet.tabComplete(args)
    }
}