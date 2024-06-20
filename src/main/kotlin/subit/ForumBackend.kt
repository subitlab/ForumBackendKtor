package subit

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import subit.console.command.CommandSet.startCommandThread
import subit.database.loadDatabaseImpl
import subit.logger.ForumLogger
import subit.plugin.*
import subit.router.router
import subit.utils.FileUtils
import subit.utils.ForumThreadGroup
import java.io.File
import kotlin.properties.Delegates

lateinit var version: String
    private set
lateinit var workDir: File
    private set
var debug by Delegates.notNull<Boolean>()
    private set

/**
 * 解析命令行, 返回的是处理后的命令行, 和从命令行中读取的配置文件(默认是config.yaml, 可通过-config=xxxx.yaml更改)
 */
private fun parseCommandLineArgs(args: Array<String>): Pair<Array<String>, File>
{
    val argsMap = args.mapNotNull {
        when (val idx = it.indexOf("="))
        {
            -1 -> null
            else -> Pair(it.take(idx), it.drop(idx + 1))
        }
    }.toMap()

    // 从命令行中加载信息

    // 工作目录
    workDir = File(argsMap["-workDir"] ?: ".")
    workDir.mkdirs()

    // 是否开启debug模式
    debug = argsMap["-debug"]?.toBoolean() ?: false

    // 去除命令行中的-config参数, 因为ktor会解析此参数进而不加载打包的application.yaml
    // 其余参数还原为字符串数组
    val resArgs = argsMap.entries
        .filterNot { it.key == "-config" || it.key == "-workDir" || it.key == "-debug" }
        .map { (k, v) -> "$k=$v" }
        .toTypedArray()
    // 命令行中输入的自定义配置文件
    // 如果输入的绝对路径, 则直接使用, 否则在工作目录下寻找
    val configFileName = argsMap["-config"] ?: "config.yaml"
    val configFile = if (configFileName.startsWith("/")) File(configFileName) else File(workDir, configFileName)

    return resArgs to configFile
}

fun main(args: Array<String>)
{
    // 处理命令行应在最前面, 因为需要来解析workDir, 否则后面的程序无法正常运行
    val (args1, configFile) = runCatching { parseCommandLineArgs(args) }.getOrElse { return }

    // 初始化配置文件加载器, 会加载所有配置文件
    subit.config.ConfigLoader.init()

    // 检查主配置文件是否存在, 不存在则创建默认配置文件, 并结束程序
    if (!configFile.exists())
    {
        configFile.createNewFile()
        val defaultConfig =
            Loader.getResource("default_config.yaml")?.readAllBytes() ?: error("default_config.yaml not found")
        configFile.writeBytes(defaultConfig)
        ForumLogger.getLogger("ForumBackend.main").severe(
            "config.yaml not found, the default config has been created, " +
            "please modify it and restart the program"
        )
        return
    }

    // 加载主配置文件
    val customConfig = ConfigLoader.load(configFile.path)

    // 生成环境
    val environment = commandLineEnvironment(args = args1)
    {
        // 将打包的application.yaml与命令行中提供的配置文件(没提供某人config.yaml)合并
        this.config = this.config.withFallback(customConfig)
    }
    // 启动服务器
    embeddedServer(Netty, environment).start(wait = true)
    // 若服务器关闭则终止整个程序
    ForumThreadGroup.shutdown(0)
}

/**
 * 应用程序入口
 */
@Suppress("unused")
fun Application.init()
{
    version = environment.config.property("version").getString()

    Loader.getResource("logo/SubIT-logo.txt")
        ?.bufferedReader()
        ?.use { it.readText().split("\n").forEach(ForumLogger.globalLogger::info) }
    ?: ForumLogger.globalLogger.warning("SubIT-logo.txt not found")

    startCommandThread()

    FileUtils.init() // 初始化文件系统

    installApiDoc()
    installAuthentication()
    installContentNegotiation()
    installCORS()
    installDoubleReceive()
    installKoin()
    installStatusPages()
    installRateLimit()

    loadDatabaseImpl()

    router()
}