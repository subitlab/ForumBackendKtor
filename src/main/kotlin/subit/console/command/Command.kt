package subit.console.command

import org.jline.reader.Candidate

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
    suspend fun execute(args: List<String>): Boolean = false

    /**
     * Tab complete the command.
     * default: empty list.
     * @param args Command arguments.
     * @return Tab complete results.
     */
    suspend fun tabComplete(args: List<String>): List<Candidate> = emptyList()
}