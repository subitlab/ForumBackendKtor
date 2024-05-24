package subit.logger

import me.nullaqua.api.reflect.CallerSensitive
import me.nullaqua.kotlin.reflect.getCallerClass
import me.nullaqua.kotlin.reflect.getCallerClasses
import subit.Loader
import subit.config.ConfigLoader
import subit.config.loggerConfig
import subit.console.AnsiStyle.Companion.RESET
import subit.console.Console
import subit.console.SimpleAnsiColor
import subit.console.SimpleAnsiColor.Companion.CYAN
import subit.console.SimpleAnsiColor.Companion.PURPLE
import subit.logger.ForumLogger.nativeOut
import subit.logger.ForumLogger.safe
import subit.workDir
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.jvm.optionals.getOrDefault
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * logger系统
 */
@Suppress("MemberVisibilityCanBePrivate")
object ForumLogger
{
    val globalLogger = LoggerUtils(Logger.getLogger(""))
    fun getLogger(name: String): LoggerUtils = LoggerUtils(Logger.getLogger(name))
    fun getLogger(clazz: KClass<*>): LoggerUtils
    {
        val c: KClass<*> = when
        {
            clazz.isCompanion -> clazz.java.enclosingClass.kotlin
            else              -> clazz
        }
        val name = c.qualifiedName ?: c.jvmName
        return getLogger(name)
    }

    fun getLogger(clazz: Class<*>): LoggerUtils = getLogger(clazz.kotlin)

    @CallerSensitive
    fun getLogger(): LoggerUtils = getCallerClass()?.let(::getLogger) ?: globalLogger
    internal val nativeOut: PrintStream = System.out
    internal val nativeErr: PrintStream = System.err

    /**
     * logger中的日期格式
     */
    val loggerDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    /**
     * 日志输出流
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val out: PrintStream = PrintStream(LoggerOutputStream(Level.INFO))

    /**
     * 日志错误流
     */
    val err: PrintStream = PrintStream(LoggerOutputStream(Level.SEVERE))
    fun addFilter(pattern: String)
    {
        loggerConfig = loggerConfig.copy(matchers = loggerConfig.matchers+pattern)
    }

    fun removeFilter(pattern: String)
    {
        loggerConfig = loggerConfig.copy(matchers = loggerConfig.matchers.filter { it != pattern })
    }

    fun setWhiteList(whiteList: Boolean)
    {
        loggerConfig = loggerConfig.copy(whiteList = whiteList)
    }

    fun setLevel(level: Level)
    {
        loggerConfig = loggerConfig.copy(levelName = level.name)
    }

    /**
     * 获取过滤器
     */
    fun filters(): MutableList<String> = Collections.unmodifiableList(loggerConfig.matchers)

    /**
     * 若由于终端相关组件错误导致的异常, 异常可能最终被捕获并打印在终端上, 可能导致再次抛出错误, 最终引起[StackOverflowError].
     * 未避免此类问题, 在涉及需要打印内容的地方, 应使用此方法.
     * 此当[block]出现错误时, 将绕过终端相关组件, 直接打印在标准输出流上. 以避免[StackOverflowError]的发生.
     */
    internal inline fun safe(block: ()->Unit)
    {
        runCatching(block).onFailure()
        {
            it.printStackTrace(nativeErr)
        }
    }

    /**
     * 初始化logger，将设置终端支持显示颜色码，捕获stdout，应在启动springboot前调用
     */
    init
    {
        System.setOut(out)
        System.setErr(err)
        globalLogger.logger.setUseParentHandlers(false)
        globalLogger.logger.handlers.forEach(globalLogger.logger::removeHandler)
        globalLogger.logger.addHandler(ToConsoleHandler)
        globalLogger.logger.addHandler(ToFileHandler)
        Loader.getResource("/logo/SubIT-logo.txt")?.copyTo(out) ?: getLogger().warning("logo not found")
        ConfigLoader.reload("logger.yml")
    }

    private class LoggerOutputStream(private val level: Level): OutputStream()
    {
        val arrayOutputStream = ByteArrayOutputStream()

        @CallerSensitive
        override fun write(b: Int) = safe()
        {
            if (b == '\n'.code)
            {
                val str: String
                synchronized(arrayOutputStream)
                {
                    str = arrayOutputStream.toString()
                    arrayOutputStream.reset()
                }
                getCallerClasses().stream()
                    .filter { !(it.packageName.startsWith("java.io")||it.packageName.startsWith("kotlin.io")) }
                    .findFirst()
                    .map(::getLogger)
                    .getOrDefault(getLogger()).logger.log(level, str)
            }
            else synchronized(arrayOutputStream) { arrayOutputStream.write(b) }
        }
    }
}

/**
 * 向终端中打印log
 */
object ToConsoleHandler: Handler()
{
    override fun publish(record: LogRecord) = safe()
    {
        if (!loggerConfig.check(record)) return
        val messages = mutableListOf(record.message)

        if (record.thrown != null)
        {
            val str = record.thrown.stackTraceToString()
            str.split("\n").forEach { messages.add(it) }
        }
        /**
         * 当[Console.println]调用的时候, 向终端打印日志, 会调用jline库,
         * 使得[org.jline.utils.StyleResolver]会在此时打印等级为FINEST的日志.
         *
         * 当该日志打印时会调用[Console.println], 再次引起日志打印, 造成无限递归.
         * 因此在这里特别处理
         */
        if (record.loggerName.startsWith("org.jline"))
        {
            /**
             * 如果等级不足INFO就不打印了
             */
            if (record.level.intValue() >= Level.INFO.intValue())
            {
                /**
                 * 如果等级大于等于INFO, 也不能直接调用[Console.println], 所以使用[nativeOut]直接打印到终端
                 */
                val head = if (loggerConfig.showLoggerName) String.format(
                    "[%s][%s][%s]",
                    ForumLogger.loggerDateFormat.format(record.millis),
                    record.loggerName,
                    record.level.name
                )
                else String.format(
                    "[%s][%s]",
                    ForumLogger.loggerDateFormat.format(record.millis),
                    record.level.name
                )
                messages.forEach { message -> nativeOut.println("$head $message") }
            }
            return
        }
        val level = record.level
        val ansiStyle = if (level.intValue() >= Level.SEVERE.intValue()) SimpleAnsiColor.RED.bright()
        else if (level.intValue() >= Level.WARNING.intValue()) SimpleAnsiColor.YELLOW.bright()
        else if (level.intValue() >= Level.CONFIG.intValue()) SimpleAnsiColor.BLUE.bright()
        else SimpleAnsiColor.GREEN.bright()
        val head = if (loggerConfig.showLoggerName) String.format(
            "%s[%s]%s[%s]%s[%s]%s",
            PURPLE.bright(),
            ForumLogger.loggerDateFormat.format(record.millis),
            CYAN.bright(),
            record.loggerName,
            ansiStyle,
            level.name,
            RESET,
        )
        else String.format(
            "%s[%s]%s[%s]%s",
            PURPLE.bright(),
            ForumLogger.loggerDateFormat.format(record.millis),
            ansiStyle,
            level.name,
            RESET,
        )

        messages.forEach { message ->
            Console.println("$head $message $RESET")
        }
    }

    override fun flush() = Unit
    override fun close() = Unit
}

/**
 * 将log写入文件
 */
object ToFileHandler: Handler()
{
    /**
     * log文件的日期格式
     */
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")

    /**
     * log文件的目录
     */
    private val logDir = File(workDir, "logs")

    /**
     * log文件
     */
    private val logFile = File(logDir, "latest.log")

    /**
     * 当前log文件的行数
     */
    private var cnt = 0

    init
    {
        new()
    }

    /**
     * 创建新的log文件
     */
    private fun new()
    {
        if (!logDir.exists()) logDir.mkdirs()
        if (logFile.exists()) // 如果文件已存在，则压缩到zip
        { // 将已有的log压缩到zip
            val zipFile = File(logDir, "${fileDateFormat.format(System.currentTimeMillis())}.zip")
            zipFile(zipFile)
            logFile.delete()
        }
        logFile.createNewFile() // 创建新的log文件
        cnt = 0 // 重置行数
    }

    /**
     * 将log文件压缩到zip
     */
    private fun zipFile(zipFile: File)
    {
        if (!logFile.exists()) return
        if (!zipFile.exists()) zipFile.createNewFile()
        val fos = FileOutputStream(zipFile)
        val zipOut = ZipOutputStream(fos)
        val fis = FileInputStream(logFile)
        val zipEntry = ZipEntry(logFile.getName())
        zipOut.putNextEntry(zipEntry)
        val bytes = ByteArray(1024)
        var length: Int
        while (fis.read(bytes).also { length = it } >= 0) zipOut.write(bytes, 0, length)
        zipOut.close()
        fis.close()
        fos.close()
    }

    private fun check()
    {
        if ((cnt ushr 10) > 0) new()
    }

    private fun append(lines: List<String>) = synchronized(this)
    {
        if (!logFile.exists()) new()
        val writer = FileWriter(logFile, true)
        lines.forEach { writer.appendLine(it) }
        writer.close()
        check()
    }

    private val colorMatcher = Regex("\u001B\\[[;\\d]*m")
    override fun publish(record: LogRecord) = safe()
    {
        if (!loggerConfig.check(record)) return
        val messages = mutableListOf(record.message)

        if (record.thrown != null)
        {
            val str = record.thrown.stackTraceToString()
            str.split("\n").forEach { messages.add(it) }
        }
        val messagesWithOutColor = messages.map { colorMatcher.replace(it, "") }
        val head = if (loggerConfig.showLoggerName) String.format(
            "[%s][%s][%s]",
            ForumLogger.loggerDateFormat.format(record.millis),
            record.level.name,
            record.loggerName
        )
        else String.format(
            "[%s][%s]",
            ForumLogger.loggerDateFormat.format(record.millis),
            record.level.name
        )
        append(messagesWithOutColor.map { "$head $it" })
    }

    override fun flush() = Unit
    override fun close() = Unit
}