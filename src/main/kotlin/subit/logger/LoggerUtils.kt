package subit.logger

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Logger Utils
 * @author nullaqua
 */
@Suppress("unused")
open class LoggerUtils(val logger: Logger)
{
    /**
     * Log a SEVERE message.
     *
     *
     * If the logger is currently enabled for the SEVERE message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     */
    fun severe(msg: String) = logger.severe(msg)

    /**
     * Log a SEVERE message with a throwable.
     *
     *
     * If the logger is currently enabled for the SEVERE message
     * level then the given message and throwable are forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     */
    fun severe(msg: String, t: Throwable) = logger.log(Level.SEVERE, msg, t)

    /**
     * Log a SEVERE message with a runnable.
     *
     *
     * If the logger is currently enabled for the SEVERE message
     * level then the given runnable is executed and the resulting message
     * is forwarded to all the registered output Handler objects.
     *
     * @param block The runnable to be executed
     * @param msg The string message (or a key in the message catalog)
     */
    inline fun severe(msg: String, block: () -> Unit) = run(Level.SEVERE, msg, block)

    /**
     * Log a WARNING message.
     *
     *
     * If the logger is currently enabled for the WARNING message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     */
    fun warning(msg: String) = logger.warning(msg)

    /**
     * Log a WARNING message with a throwable.
     *
     *
     * If the logger is currently enabled for the WARNING message
     * level then the given message and throwable are forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     * @param t   The throwable associated with the message
     */
    fun warning(msg: String, t: Throwable) = logger.log(Level.WARNING, msg, t)

    /**
     * Log a WARNING message with a runnable.
     *
     *
     * If the logger is currently enabled for the WARNING message
     * level then the given runnable is executed and the resulting message
     * is forwarded to all the registered output Handler objects.
     *
     * @param block The runnable to be executed
     * @param msg The string message (or a key in the message catalog)
     */
    inline fun warning(msg: String, block: ()->Unit) = run(Level.WARNING, msg, block)

    /**
     * Log an INFO message.
     *
     *
     * If the logger is currently enabled for the INFO message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     */
    fun info(msg: String) = logger.info(msg)

    /**
     * Log an INFO message with a throwable.
     *
     *
     * If the logger is currently enabled for the INFO message
     * level then the given message and throwable are forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     * @param t   The throwable associated with the message
     */
    fun info(msg: String, t: Throwable) = logger.log(Level.INFO, msg, t)

    /**
     * Log an INFO message with a runnable.
     *
     *
     * If the logger is currently enabled for the INFO message
     * level then the given runnable is executed and the resulting message
     * is forwarded to all the registered output Handler objects.
     *
     * @param block The runnable to be executed
     * @param msg The string message (or a key in the message catalog)
     */
    inline fun info(msg: String, block: () -> Unit) = run(Level.INFO, msg, block)

    /**
     * Log a CONFIG message.
     *
     *
     * If the logger is currently enabled for the CONFIG message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     */
    fun config(msg: String) = logger.config(msg)

    /**
     * Log a CONFIG message with a throwable.
     *
     *
     * If the logger is currently enabled for the CONFIG message
     * level then the given message and throwable are forwarded to all the
     * registered output Handler objects.
     * @param msg The string message (or a key in the message catalog)
     * @param t The throwable associated with the message
     */
    fun config(msg: String, t: Throwable) = logger.log(Level.CONFIG, msg, t)

    /**
     * Log a CONFIG message with a runnable.
     *
     *
     * If the logger is currently enabled for the CONFIG message
     * level then the given runnable is executed and the resulting message
     * is forwarded to all the registered output Handler objects.
     * @param block The runnable to be executed
     * @param msg The string message (or a key in the message catalog)
     */
    inline fun config(msg: String, block: () -> Unit) = run(Level.CONFIG, msg, block)

    /**
     * Log a FINE message.
     *
     *
     * If the logger is currently enabled for the FINE message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     */
    fun fine(msg: String) = logger.fine(msg)

    /**
     * Log a FINE message with a throwable.
     *
     *
     * If the logger is currently enabled for the FINE message
     * level then the given message and throwable are forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     * @param t   The throwable associated with the message
     */
    fun fine(msg: String, t: Throwable) = logger.log(Level.FINE, msg, t)

    /**
     * Log a FINE message with a runnable.
     *
     *
     * If the logger is currently enabled for the FINE message
     * level then the given runnable is executed and the resulting message
     * is forwarded to all the registered output Handler objects.
     *
     * @param block The runnable to be executed
     * @param msg The string message (or a key in the message catalog)
     */
    inline fun fine(msg: String, block: () -> Unit) = run(Level.FINE, msg, block)

    /**
     * Log a FINER message.
     *
     *
     * If the logger is currently enabled for the FINER message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     */
    fun finer(msg: String) = logger.finer(msg)

    /**
     * Log a FINER message with a throwable.
     *
     *
     * If the logger is currently enabled for the FINER message
     * level then the given message and throwable are forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     * @param t   The throwable associated with the message
     */
    fun finer(msg: String, t: Throwable) = logger.log(Level.FINER, msg, t)

    /**
     * Log a FINER message with a runnable.
     *
     *
     * If the logger is currently enabled for the FINER message
     * level then the given runnable is executed and the resulting message
     * is forwarded to all the registered output Handler objects.
     *
     * @param block The runnable to be executed
     * @param msg The string message (or a key in the message catalog)
     */
    inline fun finer(msg: String, block: () -> Unit) = run(Level.FINER, msg, block)

    /**
     * Log the FINEST message.
     *
     *
     * If the logger is currently enabled for the FINEST message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     */
    fun finest(msg: String) = logger.finest(msg)

    /**
     * Log the FINEST message with a throwable.
     *
     *
     * If the logger is currently enabled for the FINEST message
     * level then the given message and throwable are forwarded to all the
     * registered output Handler objects.
     *
     * @param msg The string message (or a key in the message catalog)
     * @param t   The throwable associated with the message
     */
    fun finest(msg: String, t: Throwable) = logger.log(Level.FINEST, msg, t)

    /**
     * Log the FINEST message with a runnable.
     *
     *
     * If the logger is currently enabled for the FINEST message
     * level then the given runnable is executed and the resulting message
     * is forwarded to all the registered output Handler objects.
     *
     * @param block The runnable to be executed
     * @param msg The string message (or a key in the message catalog)
     */
    inline fun finest(msg: String, block: () -> Unit) = run(Level.FINEST, msg, block)

    /**
     * Log the FINEST message with a boolean block.
     *
     *
     * If the logger is currently enabled for the FINEST message
     * level then the given boolean block is executed and the resulting message
     * is forwarded to all the registered output Handler objects.
     *
     * @param block The boolean block to be executed
     * @param msg      The string message (or a key in the message catalog)
     */
    

    inline fun run(level: Level, msg: String, block: () -> Unit) =
        runCatching(block).onFailure { logger.log(level, msg, it) }
}