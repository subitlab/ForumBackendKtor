package subit.console.command

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import subit.console.AnsiStyle
import subit.console.AnsiStyle.Companion.ansi
import subit.console.Console
import subit.console.SimpleAnsiColor
import subit.logger.ForumLogger
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream

/**
 * Command interface.
 */
interface Command
{
    /**
     * Command name.
     * default: class name without package name in lowercase.
     */
    val name: String
        get() = TreeCommand.parseName(this.javaClass.simpleName.split(".").last().lowercase())

    /**
     * Command description.
     * default: "No description."
     */
    val description: String
        get() = "No description."

    /**
     * Command args.
     * default: no args.
     */
    val args: String
        get() = "no args"

    /**
     * Command aliases.
     * default: empty list.
     */
    val aliases: List<String>
        get() = emptyList()

    /**
     * Whether to log the command.
     * default: true
     */
    val log: Boolean
        get() = true

    /**
     * Execute the command.
     * @param args Command arguments.
     * @return Whether the command is executed successfully.
     */
    fun execute(args: List<String>): Boolean = false

    /**
     * Tab complete the command.
     * default: empty list.
     * @param args Command arguments.
     * @return Tab complete results.
     */
    fun tabComplete(args: List<String>): List<Candidate> = emptyList()
}

/**
 * Command set.
 */
object CommandSet: TreeCommand()
{
    /**
     * Register all commands.
     */
    fun registerAll()
    {
        addCommand(Reload)
        addCommand(Stop)
        addCommand(Help)
        addCommand(About)
        addCommand(Clear)
        addCommand(Logger)
        addCommand(Shell)
        addCommand(Color)
        addCommand(Whitelist)
    }

    /**
     * Command completer.
     */
    object CommandCompleter: Completer
    {
        override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>?)
        {
            try
            {
                candidates?.addAll(CommandSet.tabComplete(line.words().subList(0, line.wordIndex()+1)))
            }
            catch (e: Throwable)
            {
                ForumLogger.severe("An error occurred while tab completing", e)
            }
        }
    }

    /**
     * 命令输出流,格式是[COMMAND][INFO/Error]message
     */
    private class CommandOutputStream(private val style: AnsiStyle, private val level:String): OutputStream()
    {
        val arrayOutputStream = ByteArrayOutputStream()
        override fun write(b: Int)
        {
            if (b=='\n'.code)
            {
                Console.println("${SimpleAnsiColor.PURPLE.bright()}[COMMAND]$style$level${AnsiStyle.RESET} $arrayOutputStream")
                arrayOutputStream.reset()
            }
            else
            {
                arrayOutputStream.write(b)
            }
        }
    }

    /**
     * Command output stream.
     */
    val out: PrintStream = PrintStream(CommandOutputStream(SimpleAnsiColor.BLUE.bright().ansi(),"[INFO]"))

    /**
     * Command error stream.
     */
    val err: PrintStream = PrintStream(CommandOutputStream(SimpleAnsiColor.RED.bright().ansi(),"[ERROR]"))
}
