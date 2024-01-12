package subit.console.command

import org.jline.reader.Candidate
import java.util.*

/**
 * 树形命令, 即一个命令下面还有子命令
 */
abstract class TreeCommand(): Command
{
    companion object
    {
        /**
         * 将名字转换为合法的命令名(小写, 去掉空格)
         */
        fun parseName(name: String): String
        {
            return name.toCharArray().filter { !it.isWhitespace() }.joinToString("").lowercase()
        }
    }

    constructor(vararg command: Command): this()
    {
        command.forEach { addCommand(it) }
    }

    private val map = HashMap<String, Command>()

    /**
     * 添加一条命令
     */
    fun addCommand(command: Command)
    {
        map[parseName(command.name)] = command
        command.aliases.forEach { map[parseName(it)] = command }
    }

    /**
     * 通过名字/别名获取命令对象
     */
    fun getCommand(name: String): Command? = map[parseName(name)]

    /**
     * 所有命令对象
     */
    fun allCommands(): Set<Command> = Collections.unmodifiableSet(HashSet(map.values))

    /**
     * 所有命令名/别名
     */
    fun allCommandNames(): Set<String> = Collections.unmodifiableSet(map.keys)

    override val args: String
        get()
        {
            val sb: StringBuilder = StringBuilder()
            for (cmd in allCommands())
            {
                sb.append(parseName(cmd.name))
                if (cmd.aliases.isNotEmpty())
                {
                    sb.append("|")
                    sb.append(cmd.aliases.joinToString("|"))
                }
                val s = cmd.args.split("\n")
                if (s.size>1)
                {
                    sb.append(":\n")
                    s.forEach { sb.append("  $it\n") }
                }
                else sb.append(": ${s[0]}\n")
            }

            return sb.removeSuffix("\n").toString()
        }

    /**
     * 执行命令
     */
    override fun execute(args: List<String>): Boolean
    {
        if (args.isEmpty()) return false // 参数过短
        val command = map[args[0]] // 获取命令对象
        return if (command==null) // 如果命令对象不存在
        {
            CommandSet.err.println("Unknown argument: ${args[0]}")
            true
        }
        else command.execute(args.drop(1)) // 删掉第一个参数并给到下一层
    }

    /**
     * Tab complete
     */
    override fun tabComplete(args: List<String>): List<Candidate>
    {
        // 如果没有参数或者只有一个参数, 就返回所有命令名/别名
        if (args.isEmpty()||args.size==1) return map.entries.map {
            Candidate(it.key, it.key, null, null, null, parseName(it.value.name), true, 0)
        }
        // 否则就获取命令对象并交给下一层
        val command = map[args[0]] ?: return emptyList()
        return command.tabComplete(args.drop(1))
    }
}