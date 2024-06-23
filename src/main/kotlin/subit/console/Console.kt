package subit.console

import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.LineReaderImpl
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.widget.AutopairWidgets
import org.jline.widget.AutosuggestionWidgets
import subit.console.command.CommandSet
import subit.console.command.CommandSet.err
import subit.logger.ForumLogger
import subit.utils.FileUtils
import subit.utils.Power
import sun.misc.Signal
import java.io.File

/**
 * 终端相关
 */
object Console
{
    /**
     * 终端对象
     */
    private val terminal: Terminal = TerminalBuilder.builder().jansi(true).build()

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

    init
    {
        Signal.handle(Signal("INT")) { onUserInterrupt() }

        // 自动配对(小括号/中括号/大括号/引号等)
        val autopairWidgets = AutopairWidgets(lineReader, true)
        autopairWidgets.enable()
        // 根据历史记录建议
        val autosuggestionWidgets = AutosuggestionWidgets(lineReader)
        autosuggestionWidgets.enable()
    }

    fun onUserInterrupt()
    {
        err.println("You might have pressed Ctrl+C or performed another operation to stop the server.")
        err.println("This method is feasible but not recommended," +
                    " it should only be used when a command-line system error prevents the program from closing.")
        err.println("If you want to stop the server, please use the \"stop\" command.")
        Power.shutdown(0, "User interrupt")
    }

    /**
     * 命令历史文件
     */
    private val historyFile: File
        get() = File(FileUtils.dataFolder,"command_history.txt")

    /**
     * 在终端上打印一行, 会自动换行并下移命令提升符和已经输入的命令
     */
    fun println(o: Any) = if (lineReader.isReading) lineReader.printAbove("\r$o") else terminal.writer().println(o)

    /**
     * 清空终端
     */
    fun clear()
    {
        ForumLogger.nativeOut.print("\u001bc")
        lineReader as LineReaderImpl
        if (lineReader.isReading) lineReader.redrawLine()
    }
}