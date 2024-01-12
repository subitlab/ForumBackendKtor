package subit

import kotlinx.serialization.decodeFromString
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlMap
import org.fusesource.jansi.AnsiConsole
import subit.console.Console
import subit.console.command.CommandSet
import subit.logger.ForumLogger
import java.io.File
import java.io.InputStream

object Loader
{
    /**
     * 是否已经初始化
     */
    private var initiated = false

    /**
     * 需要加载的配置文件
     */
    private val configs = setOf(
        "log.yml"
    )

    /**
     * 重载后需要执行的任务
     */
    private val tasks: Set<()->Unit> = setOf(
        ForumLogger::load,
    )

    /**
     * 初始化
     */
    fun init()
    {
        if (initiated) return
        initiated = true
        AnsiConsole.systemInstall() // 支持终端颜色码
        CommandSet.registerAll() // 注册所有命令
        Loader() // 加载配置文件
        Console.init() // 初始化终端(启动命令处理线程)
    }

    /**
     * 加载配置文件
     */
    operator fun invoke(): Loader
    {
        for (filename in configs)
        for (task in tasks) task()
        return this
    }

    inline fun <reified T> getConfig(filename: String):T = Yaml.decodeFromString(getConfigFile(filename).readText())
    inline fun <reified T> getConfig(filename: String, default: T):T = runCatching { getConfig<T>(filename) }.getOrDefault(default)
    inline fun <reified T> getConfigOrNull(filename: String):T? = runCatching { getConfig<T>(filename) }.getOrNull()
    inline fun <reified T> getConfigOrCreate(filename: String, default: T):T =
        getConfigOrNull<T>(filename) ?: default.also { saveConfig(filename, it) }
    fun getConfigAsMap(filename: String) = Yaml.decodeYamlMapFromString(getConfigFile(filename).readText())
    fun getConfigAsMap(filename: String, default: YamlMap) = runCatching { getConfigAsMap(filename) }.getOrDefault(default)


    inline fun <reified T> saveConfig(filename: String, config: T)
    {
        getConfigFile(filename).writeText(Yaml.encodeToString(config))
    }

    fun getConfigFile(filename: String): File
    {
        val file = File("configs/$filename")
        if (!file.exists())
        {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        return file
    }

    fun getResources(path: String): InputStream? = javaClass.classLoader.getResource(path)?.openStream()
}