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
     * 执行加载任务
     */
    operator fun invoke(): Loader
    {
        for (task in tasks) task()
        return this
    }

    /**
     * 从配置文件中获取配置, 需要T是可序列化的.在读取失败时抛出错误
     * @param filename 配置文件名
     * @return 配置
     */
    inline fun <reified T> getConfig(filename: String): T = Yaml.decodeFromString(getConfigFile(filename).readText())

    /**
     * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回默认值
     * @param filename 配置文件名
     * @param default 默认值
     * @return 配置
     */
    inline fun <reified T> getConfig(filename: String, default: T): T = runCatching { getConfig<T>(filename) }.getOrDefault(default)

    /**
     * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回null
     * @param filename 配置文件名
     * @return 配置
     */
    inline fun <reified T> getConfigOrNull(filename: String): T? = runCatching { getConfig<T>(filename) }.getOrNull()

    /**
     * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回默认值,并将默认值写入配置文件
     * @param filename 配置文件名
     * @param default 默认值
     * @return 配置
     */
    inline fun <reified T> getConfigOrCreate(filename: String, default: T): T =
        getConfigOrNull<T>(filename) ?: default.also { saveConfig(filename, it) }

    /**
     * 保存配置到文件
     * @param filename 配置文件名
     * @param config 配置
     */
    inline fun <reified T> saveConfig(filename: String, config: T) = createConfigFile(filename).writeText(Yaml.encodeToString(config))

    /**
     * 获取配置文件
     * @param filename 配置文件名
     * @return 配置文件
     */
    fun getConfigFile(filename: String): File = File("configs/$filename")

    /**
     * 获取配置文件, 如果不存在则创建
     * @param filename 配置文件名
     * @return 配置文件
     */
    fun createConfigFile(filename: String): File = getConfigFile(filename).also { it.parentFile.mkdirs();it.createNewFile() }

    /**
     * 获取资源文件
     * @param path 资源路径
     * @return 资源文件输入流
     */
    fun getResources(path: String): InputStream? = javaClass.classLoader.getResource(path)?.openStream()
}