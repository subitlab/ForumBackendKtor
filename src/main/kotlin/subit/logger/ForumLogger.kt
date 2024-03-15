package subit.logger

import kotlinx.serialization.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.nullaqua.api.util.LoggerUtils
import net.mamoe.yamlkt.Comment
import subit.Loader
import subit.console.AnsiStyle
import subit.console.Console
import subit.console.SimpleAnsiColor
import java.io.*
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.jvm.optionals.getOrNull

/**
 * logger系统
 */
object ForumLogger: LoggerUtils(Logger.getLogger(""))
{
    /**
     * logger中的日期格式
     */
    val loggerDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private const val LOGGER_SETTINGS_FILE = "log.yml"

    @Serializable
    data class LoggerFilter(
        @Comment("过滤器，根据whiteList决定符合哪些条件的log会被打印/过滤")
        val matchers: List<String> = arrayListOf(),
        @Comment("是否为白名单模式，如果为true，则只有符合matchers的log会被打印，否则只有不符合matchers的log会被打印")
        val whiteList: Boolean = true,
        @Comment("日志等级 (OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL)")
        @Serializable(with = LevelSerializer::class)
        val level: Level = Level.INFO,
        @Transient
        val pattern: Pattern = Pattern.compile(matchers.joinToString("|") { "($it)" })
    )
    {
        fun check(record: LogRecord): Boolean = (matchers.isEmpty()||pattern.matcher(record.message).find()==whiteList)
                &&(record.loggerName?.startsWith("org.jline")!=true||record.level.intValue()>=Level.INFO.intValue())

        override fun toString() = "LoggerFilter(matchers=$matchers, whiteList=$whiteList, level=$level)"

        object LevelSerializer: KSerializer<Level>
        {
            @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
            override val descriptor: SerialDescriptor = buildSerialDescriptor("LevelSerializer", PrimitiveKind.STRING)
            override fun deserialize(decoder: Decoder): Level = Level.parse(decoder.decodeString())
            override fun serialize(encoder: Encoder, value: Level) = encoder.encodeString(value.name)

        }
    }

    var filter = LoggerFilter()
        set(value)
        {
            field = value
            logger().level = value.level
        }

    /**
     * 添加过滤器
     */
    fun addFilter(pattern: String)
    {
        filter = filter.copy(matchers = filter.matchers+pattern)
    }

    /**
     * 移除过滤器
     */
    fun removeFilter(pattern: String)
    {
        filter = filter.copy(matchers = filter.matchers.filter { it!=pattern })
    }

    /**
     * 设置白名单模式
     */
    fun setWhiteList(whiteList: Boolean)
    {
        filter = filter.copy(whiteList = whiteList)
    }

    /**
     * 设置日志等级
     */
    fun setLevel(level: Level)
    {
        filter = filter.copy(level = level)
    }

    /**
     * 获取过滤器
     */
    fun filters(): MutableList<String> = Collections.unmodifiableList(filter.matchers)

    /**
     * 初始化logger，将设置终端支持显示颜色码，捕获stdout，应在启动springboot前调用
     */
    fun load()
    {
        System.setOut(out)
        System.setErr(err)
        ForumLogger.logger().setUseParentHandlers(false)
        ForumLogger.logger().handlers.forEach { ForumLogger.logger().removeHandler(it) }
        ForumLogger.logger().addHandler(ToConsoleHandler)
        ForumLogger.logger().addHandler(ToFileHandler)
        ForumLogger.logger().addHandler(object: Handler()
        {
            override fun publish(record: LogRecord?)
            {
                val throwable = record?.thrown ?: return
                val out = ByteArrayOutputStream()
                throwable.printStackTrace(PrintStream(out))
                out.toString().split('\n').forEach { ForumLogger.logger().log(record.level, it) }
            }

            override fun flush() = Unit
            override fun close() = Unit
        })
        loadConfig()
    }

    /**
     * 从文件中加载配置
     */
    fun loadConfig(file: String = LOGGER_SETTINGS_FILE)
    {
        filter = Loader.getConfigOrCreate(file, filter)
        info("log filter: $filter")
    }

    /**
     * 保存配置到文件
     */
    fun saveConfig(file: String = LOGGER_SETTINGS_FILE) = Loader.saveConfig(file, filter)

    /**
     * 用于在方法中调用，会自动记录方法名和参数,是否出错等内容
     *
     * # 示例:
     *
     * ```kotlin
     *     fun example(a: Any?,b: Any?,c: Any?,...): Any = log(a,b,c,...)
     *     {
     *         // do something
     *         return@log something
     *     }
     * ```
     * @param args 函数调用的参数
     * @param block 要执行的代码块
     */
    fun <T> log(vararg args: Any?, block: ()->T): T =
        runCatching(block).onSuccess { logFromMethod(null, *args) }.onFailure { logFromMethod(it, *args) }.getOrThrow()

    /**
     * 用于在方法中调用，会记录方法名和参数,是否出错等内容
     */
    private fun logFromMethod(throwable: Throwable?, vararg msg: Any?)
    {
        val callerClass = Arrays.stream(Thread.currentThread().stackTrace)
            .filter { it.className!=ForumLogger::class.java.name&&it.className.startsWith("subit.forum.backend") }
            .map { it.className }
            .map { runCatching { Class.forName(it) }.getOrNull() }
            .findFirst()
            .orElse(null) ?: return
        val callerMethod = Arrays.stream(Thread.currentThread().stackTrace)
            .filter { it.className==callerClass.name }
            .map { it.methodName }
            .findFirst()
            .getOrNull()
        val method = if (callerMethod!=null) Arrays.stream(callerClass.declaredMethods)
            .filter { method: Method -> method.name==callerMethod&&method.parameterCount==msg.size }
            .findFirst()
            .getOrNull()
        else null
        val sb: StringBuilder = StringBuilder()
        sb.append(callerClass.name.split('.').last())
        sb.append("#")
        sb.append(callerMethod)
        sb.append("(")
        if (method!=null)
        {
            for (i in 0 until method.parameterCount)
            {
                sb.append(method.parameters[i].name.split('.').last())
                sb.append(":")
                sb.append(msg[i])
                if (i!=method.parameterCount-1) sb.append(", ")
            }
        }
        else
        {
            for (i in msg.indices)
            {
                sb.append(msg[i])
                if (i!=msg.size-1) sb.append(", ")
            }
        }
        sb.append(")")
        val record = LogRecord(Level.FINE, sb.toString())
        record.setSourceClassName(callerClass.name)
        record.setSourceMethodName(callerMethod)
        record.thrown = throwable
        if (throwable!=null) record.level = Level.WARNING
        logger().log(record)
    }

    private class LoggerOutputStream(private val level: Level): OutputStream()
    {
        val arrayOutputStream = ByteArrayOutputStream()
        override fun write(b: Int)
        {
            if (b=='\n'.code)
            {
                ForumLogger.logger().log(level, arrayOutputStream.toString())
                arrayOutputStream.reset()
            }
            else
            {
                arrayOutputStream.write(b)
            }
        }
    }

    /**
     * 日志输出流
     */
    val out: PrintStream = PrintStream(LoggerOutputStream(Level.INFO))

    /**
     * 日志错误流
     */
    val err: PrintStream = PrintStream(LoggerOutputStream(Level.SEVERE))
}

/**
 * 向终端中打印log
 */
object ToConsoleHandler: Handler()
{
    override fun publish(record: LogRecord)
    {
        if (!ForumLogger.filter.check(record)) return
        val level = record.level
        val ansiStyle = if (level.intValue()>=Level.SEVERE.intValue()) SimpleAnsiColor.RED.bright()
        else if (level.intValue()>=Level.WARNING.intValue()) SimpleAnsiColor.YELLOW.bright()
        else if (level.intValue()>=Level.CONFIG.intValue()) SimpleAnsiColor.BLUE.bright()
        else SimpleAnsiColor.GREEN.bright() // if level.name.length<5 then add space

        Console.println(
            String.format(
                "%s[%s]%s[%s]%s %s",
                SimpleAnsiColor.PURPLE.bright(),
                ForumLogger.loggerDateFormat.format(record.millis),
                ansiStyle,
                level.name,
                AnsiStyle.RESET,
                record.message
            )
        )
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
    private val logDir = File("logs")

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
        while (fis.read(bytes).also { length = it }>=0) zipOut.write(bytes, 0, length)
        zipOut.close()
        fis.close()
        fos.close()
    }

    override fun publish(record: LogRecord)
    {
        if (!ForumLogger.filter.check(record)) return
        if (!logFile.exists()) new()
        val fos = FileOutputStream(logFile, true)
        fos.write(String.format("[%s][%s] %s\n", ForumLogger.loggerDateFormat.format(record.millis), record.level.name, record.message).toByteArray())
        fos.close()
        ++cnt
        if ((cnt ushr 10)>0) new()
    }

    override fun flush()
    {
    }

    override fun close()
    {
    }
}