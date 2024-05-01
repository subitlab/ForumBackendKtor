package subit.console.command

import io.ktor.server.application.*
import org.jline.reader.*
import org.jline.reader.impl.DefaultParser
import subit.console.AnsiStyle
import subit.console.AnsiStyle.Companion.ansi
import subit.console.Console
import subit.console.SimpleAnsiColor
import subit.logger.ForumLogger
import subit.utils.ForumThreadGroup
import subit.utils.ForumThreadGroup.shutdown
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
object CommandSet: TreeCommand(
    Reload,
    Stop,
    Help,
    About,
    Clear,
    Logger,
    Shell,
    Color,
    Whitelist
)
{
    /**
     * 上一次命令是否成功
     */
    private var success = true

    /**
     * 命令提示符, 上一次成功为青色, 失败为红色
     */
    private val prompt: String
        get() = "${if (success) SimpleAnsiColor.CYAN.bright() else SimpleAnsiColor.RED.bright()}FORUM > ${AnsiStyle.RESET}"

    fun Application.startCommandThread()
    {
        ForumThreadGroup.newThread("CommandThread")
        {
            var line: String? = null
            while (true) try
            {
                line = Console.lineReader.readLine(prompt)
                val words = DefaultParser().parse(line, 0, Parser.ParseContext.ACCEPT_LINE).words()
                if (words.isEmpty()||(words.size==1&&words.first().isEmpty())) continue
                val command = CommandSet.getCommand(words[0])
                if (command==null||command.log) ForumLogger.info("Console is used command: $line")
                success = false
                if (command==null)
                {
                    err.println("Unknown command: ${words[0]}, use \"help\" to get help")
                }
                else if (!command.execute(words.subList(1, words.size)))
                {
                    err.println("Command is illegal, use \"help ${words[0]}\" to get help")
                }
                else success = true
            }
            catch (e: UserInterruptException)
            {
                ForumLogger.warning("Console is interrupted")
                return@newThread
            }
            catch (e: EndOfFileException)
            {
                ForumLogger.warning("Console is closed")
                shutdown(0)
            }
            catch (e: Exception)
            {
                ForumLogger.severe("An error occurred while processing the command${line ?: ""}", e)
            }
            catch (e: Error)
            {
                ForumLogger.severe("An error occurred while processing the command${line ?: ""}", e)
            }
            catch (e: RuntimeException)
            {
                ForumLogger.severe("An error occurred while processing the command${line ?: ""}", e)
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
        ForumLogger.config("Console is initialized")
    }

    /**
     * Command completer.
     */
    object CommandCompleter: Completer
    {
        override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>?)
        {
            ForumLogger.severe("An error occurred while tab completing")
            {
                candidates?.addAll(CommandSet.tabComplete(line.words().subList(0, line.wordIndex()+1)))
            }
        }
    }

    /**
     * 命令输出流,格式是[COMMAND][INFO/ERROR]message
     */
    private class CommandOutputStream(private val style: AnsiStyle, private val level: String): OutputStream()
    {
        val arrayOutputStream = ByteArrayOutputStream()
        override fun write(b: Int)
        {
            if (b == '\n'.code)
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
    val out: PrintStream = PrintStream(CommandOutputStream(SimpleAnsiColor.BLUE.bright().ansi(), "[INFO]"))

    /**
     * Command error stream.
     */
    val err: PrintStream = PrintStream(CommandOutputStream(SimpleAnsiColor.RED.bright().ansi(), "[ERROR]"))
}
