package subit.logger

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.StackTraceElementProxy
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import org.fusesource.jansi.AnsiConsole
import java.time.Instant
import java.util.*
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * 使用[ForumLogger]接管ktor的logger
 */
class LogbackAppender: AppenderBase<ILoggingEvent>()
{
    override fun append(event: ILoggingEvent) = runCatching()
    {
        val record = LogRecord(fromInt(event.level.levelInt), event.formattedMessage)
        record.setInstant(Instant.ofEpochMilli(event.timeStamp))
        record.loggerName = event.loggerName
        record.parameters = event.argumentArray
        record.setSourceClassName(event.callerData[0].className)
        record.setSourceMethodName(event.callerData[0].methodName)
        if (event.throwableProxy is ThrowableProxy)
        {
            record.thrown = (event.throwableProxy as ThrowableProxy).throwable
        }
        else if (event.throwableProxy!=null)
        {
            val throwable = Throwable(event.throwableProxy.message)
            throwable.setStackTrace(
                Arrays.stream(event.throwableProxy.stackTraceElementProxyArray)
                    .map { obj: StackTraceElementProxy -> obj.stackTraceElement }
                    .toArray { size: Int -> arrayOfNulls(size) })
            record.thrown = throwable
        }
        ForumLogger.globalLogger.logger.log(record)
    }.onFailure { AnsiConsole.sysOut().println("Failure in LogbackAppender, message: ${it.stackTraceToString()}"); }.run { }

    private fun fromInt(id: Int): Level
    {
        if (id>=ch.qos.logback.classic.Level.OFF_INT) return Level.OFF
        if (id>=ch.qos.logback.classic.Level.ERROR_INT) return Level.SEVERE
        if (id>=ch.qos.logback.classic.Level.WARN_INT) return Level.WARNING
        if (id>=ch.qos.logback.classic.Level.INFO_INT) return Level.INFO
        if (id>=ch.qos.logback.classic.Level.DEBUG_INT) return Level.CONFIG
        if (id>=ch.qos.logback.classic.Level.TRACE_INT) return Level.FINER
        return Level.ALL
    }
}