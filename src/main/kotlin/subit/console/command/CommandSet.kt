package subit.console.command

import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jline.reader.*
import org.jline.reader.impl.DefaultParser
import subit.console.AnsiStyle
import subit.console.AnsiStyle.Companion.RESET
import subit.console.AnsiStyle.Companion.ansi
import subit.console.Console
import subit.console.SimpleAnsiColor
import subit.logger.ForumLogger
import subit.utils.Power.shutdown
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream

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
    Whitelist,
    Maintain,
    TestDatabase
)
{
    private val logger = ForumLogger.getLogger()

    /**
     * 上一次命令是否成功
     */
    private var success = true
    private fun parsePrompt(prompt: String): String =
        "${if (success) SimpleAnsiColor.CYAN.bright() else SimpleAnsiColor.RED.bright()}$prompt${RESET}"

    /**
     * 命令提示符, 上一次成功为青色, 失败为红色
     */
    private val prompt: String = parsePrompt("FORUM > ")
    private val rightPrompt: String = parsePrompt("<| POWERED BY SUBIT |>")

    fun Application.startCommandThread() = CoroutineScope(Dispatchers.IO).launch()
    {
        var line: String? = null
        while (true) try
        {
            line = Console.lineReader.readLine(prompt, rightPrompt, null as Char?, null)
            val words = DefaultParser().parse(line, 0, Parser.ParseContext.ACCEPT_LINE).words()
            if (words.isEmpty() || (words.size == 1 && words.first().isEmpty())) continue
            val command = CommandSet.getCommand(words[0])
            if (command == null || command.log) logger.info("Console is used command: $line")
            success = false
            if (command == null)
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
            Console.onUserInterrupt()
        }
        catch (e: EndOfFileException)
        {
            logger.warning("Console is closed")
            shutdown(0, "Console is closed")
        }
        catch (e: Throwable)
        {
            logger.severe("An error occurred while processing the command${line ?: ""}", e)
        }
        finally
        {
            line = null
        }
    }.start()

    /**
     * Command completer.
     */
    object CommandCompleter: Completer
    {
        override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>?)
        {
            logger.severe("An error occurred while tab completing")
            {
                val candidates1 = runBlocking { CommandSet.tabComplete(line.words().subList(0, line.wordIndex() + 1)) }
                candidates?.addAll(candidates1)
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
                Console.println("${SimpleAnsiColor.PURPLE.bright()}[COMMAND]$style$level$RESET $arrayOutputStream")
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