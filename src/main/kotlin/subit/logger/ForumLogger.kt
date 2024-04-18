package subit.logger

import kotlinx.serialization.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.mamoe.yamlkt.Comment
import subit.Loader
import subit.console.AnsiStyle
import subit.console.Console
import subit.console.SimpleAnsiColor
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    /**
     * 日志输出流
     */
    val out: PrintStream = PrintStream(LoggerOutputStream(Level.INFO))

    /**
     * 日志错误流
     */
    val err: PrintStream = PrintStream(LoggerOutputStream(Level.SEVERE))
    var filter = LoggerFilter()
        set(value)
        {
            field = value
            logger.level = value.level
        }

    fun addFilter(pattern: String) = run { filter = filter.copy(matchers = filter.matchers+pattern) }
    fun removeFilter(pattern: String) = run { filter = filter.copy(matchers = filter.matchers.filter { it != pattern }) }
    fun setWhiteList(whiteList: Boolean) = run { filter = filter.copy(whiteList = whiteList) }
    fun setLevel(level: Level) = run { filter = filter.copy(level = level) }

    /**
     * 获取过滤器
     */
    fun filters(): MutableList<String> = Collections.unmodifiableList(filter.matchers)

    /**
     * 初始化logger，将设置终端支持显示颜色码，捕获stdout，应在启动springboot前调用
     */
    init
    {
        System.setOut(out)
        System.setErr(err)
        ForumLogger.logger.setUseParentHandlers(false)
        ForumLogger.logger.handlers.forEach { ForumLogger.logger.removeHandler(it) }
        ForumLogger.logger.addHandler(ToConsoleHandler)
        ForumLogger.logger.addHandler(ToFileHandler)
        ForumLogger.logger.addHandler(object: Handler()
        {
            override fun publish(record: LogRecord?)
            {
                val throwable = record?.thrown ?: return
                val out = ByteArrayOutputStream()
                throwable.printStackTrace(PrintStream(out))
                out.toString().split('\n').forEach { ForumLogger.logger.log(record.level, it) }
            }

            override fun flush() = Unit
            override fun close() = Unit
        })
        loadConfig()
        Loader.reloadTasks.add(::loadConfig)
        Loader.getResource("/logo/SubIT-logo.txt")?.copyTo(out) ?: warning("logo not found")
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
    private class LoggerOutputStream(private val level: Level): OutputStream()
    {
        val arrayOutputStream = ByteArrayOutputStream()
        override fun write(b: Int)
        {
            if (b == '\n'.code)
            {
                ForumLogger.logger.log(level, arrayOutputStream.toString())
                arrayOutputStream.reset()
            }
            else
            {
                arrayOutputStream.write(b)
            }
        }
    }

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
        fun check(record: LogRecord): Boolean = (matchers.isEmpty() || pattern.matcher(record.message).find() == whiteList)
                                                && (record.loggerName?.startsWith("org.jline") != true || record.level.intValue() >= Level.INFO.intValue())

        override fun toString() = "LoggerFilter(matchers=$matchers, whiteList=$whiteList, level=$level)"

        object LevelSerializer: KSerializer<Level>
        {
            @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
            override val descriptor: SerialDescriptor = buildSerialDescriptor("LevelSerializer", PrimitiveKind.STRING)
            override fun deserialize(decoder: Decoder): Level = Level.parse(decoder.decodeString())
            override fun serialize(encoder: Encoder, value: Level) = encoder.encodeString(value.name)
        }
    }
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
        val ansiStyle = if (level.intValue() >= Level.SEVERE.intValue()) SimpleAnsiColor.RED.bright()
        else if (level.intValue() >= Level.WARNING.intValue()) SimpleAnsiColor.YELLOW.bright()
        else if (level.intValue() >= Level.CONFIG.intValue()) SimpleAnsiColor.BLUE.bright()
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
        while (fis.read(bytes).also { length = it } >= 0) zipOut.write(bytes, 0, length)
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
        if ((cnt ushr 10) > 0) new()
    }

    override fun flush() = Unit
    override fun close() = Unit
}