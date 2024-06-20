package subit.console.command

import org.jline.reader.Candidate
import subit.config.ConfigLoader

/**
 * Reload configs.
 */
object Reload: Command
{
    override val description = "Reload configs."

    override suspend fun execute(args: List<String>): Boolean
    {
        if (args.isEmpty()) ConfigLoader.reloadAll()
        else if (args.size == 1) ConfigLoader.reload(args[0])
        else return false
        return true
    }

    override suspend fun tabComplete(args: List<String>): List<Candidate> = ConfigLoader.configs().map(::Candidate)
}