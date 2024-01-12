package subit.console

import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.Parser
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import subit.console.command.CommandSet
import subit.logger.ForumLogger
import java.io.File

/**
 * 终端相关
 */
object Console
{
    /**
     * 终端对象
     */
    val terminal: Terminal = TerminalBuilder.builder().jansi(true).build()

    /**
     * 颜色显示模式
     */
    var ansiColorMode: ColorDisplayMode = ColorDisplayMode.RGB
    /**
     * 效果显示模式
     */
    var ansiEffectMode: EffectDisplayMode = EffectDisplayMode.ON

    /**
     * 命令行读取器,命令补全为[CommandSet.CommandCompleter],命令历史保存在[historyFile]中
     */
    private val lineReader: LineReader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(CommandSet.CommandCompleter)
        .variable(LineReader.HISTORY_FILE, historyFile)
        .build()

    /**
     * 上一次命令是否成功
     */
    private var success = true

    /**
     * 命令提示符, 上一次成功为青色, 失败为红色
     */
    private val prompt: String
        get() = "${if (success) SimpleAnsiColor.CYAN.bright() else SimpleAnsiColor.RED.bright()}FORUM > ${AnsiStyle.RESET}"

    /**
     * 命令历史文件
     */
    private val historyFile: File
        get() = File("data/command_history.txt")

    /**
     * 在终端上打印一行, 会自动换行并下移命令提升符和已经输入的命令
     */
    fun println(o: Any) = lineReader.printAbove("$o")

    /**
     * 清空终端
     */
    fun clear() = terminal.puts(InfoCmp.Capability.clear_screen)

    /**
     * 初始化终端
     */
    fun init()
    {
        Thread {
            var line: String? = null
            while (true) try
            {
                line = lineReader.readLine(prompt)
                val words = DefaultParser().parse(line, 0, Parser.ParseContext.ACCEPT_LINE).words()
                if (words.isEmpty()||(words.size==1&&words.first().isEmpty())) continue
                val command = CommandSet.getCommand(words[0])
                if (command==null||command.log) ForumLogger.info("Console is used command: $line")
                success = false
                if (command==null)
                {
                    CommandSet.err.println("Unknown command: ${words[0]}, use \"help\" to get help")
                }
                else if (!command.execute(words.subList(1, words.size)))
                {
                    CommandSet.err.println("Command is illegal, use \"help ${words[0]}\" to get help")
                }
                else success = true
            }
            catch (e: Throwable)
            {
                ForumLogger.severe("An error occurred while processing the command${line ?: ""}", e)
            }
            finally
            {
                line = null
            }
        }.start()
    }
}