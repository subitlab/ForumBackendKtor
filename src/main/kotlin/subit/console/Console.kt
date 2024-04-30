package subit.console

import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import subit.console.command.CommandSet
import subit.utils.FileUtils
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
    val lineReader: LineReader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(CommandSet.CommandCompleter)
        .variable(LineReader.HISTORY_FILE, historyFile)
        .build()

    /**
     * 命令历史文件
     */
    private val historyFile: File
        get() = File(FileUtils.dataFolder,"command_history.txt")

    /**
     * 在终端上打印一行, 会自动换行并下移命令提升符和已经输入的命令
     */
    fun println(o: Any) = lineReader.printAbove("$o")

    /**
     * 清空终端
     */
    fun clear() = terminal.puts(InfoCmp.Capability.clear_screen)
}