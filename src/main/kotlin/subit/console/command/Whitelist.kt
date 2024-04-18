package subit.console.command

import kotlinx.coroutines.runBlocking
import org.jline.reader.Candidate
import subit.console.AnsiStyle
import subit.console.SimpleAnsiColor
import subit.database.WhitelistDatabase

object Whitelist: TreeCommand(Add, Remove, Get)
{
    override val description: String
        get() = "Whitelist manage."

    object Add: Command
    {
        override val description: String
            get() = "Add an email to whitelist."
        override val args: String
            get() = "<email>"

        override fun execute(args: List<String>): Boolean
        {
            if (args.size != 1) return false
            runBlocking {
                WhitelistDatabase.add(args[0])
            }
            CommandSet.out.println("添加成功")
            return true
        }
    }

    object Remove: Command
    {
        override val description: String
            get() = "Remove an email from whitelist."
        override val args: String
            get() = "<email>"

        override fun execute(args: List<String>): Boolean
        {
            if (args.size != 1) return false
            runBlocking {
                WhitelistDatabase.remove(args[0])
            }
            CommandSet.out.println("移除成功")
            return true
        }
        override fun tabComplete(args: List<String>): List<Candidate>
        {
            if (args.size == 1)
            {
                return runBlocking { WhitelistDatabase.getWhitelist() }.map { Candidate(it) }
            }
            return emptyList()
        }
    }

    object Get: Command
    {
        override val description: String
            get() = "列出白名单"
        override val args: String
            get() = ""

        override fun execute(args: List<String>): Boolean
        {
            runBlocking {
                WhitelistDatabase.getWhitelist().apply {
                    if (isEmpty()) CommandSet.out.println("白名单为空")
                    else
                    {
                        CommandSet.out.println("白名单:")
                        forEach{ CommandSet.out.println("${SimpleAnsiColor.GREEN}-${AnsiStyle.RESET} $it") }
                    }
                }
            }
            return true
        }
    }
}