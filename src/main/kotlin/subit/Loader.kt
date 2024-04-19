package subit

import kotlinx.serialization.decodeFromString
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlElement
import net.mamoe.yamlkt.YamlMap
import subit.logger.ForumLogger
import java.io.File
import java.io.InputStream

object Loader
{
    /**
     * 重载后需要执行的任务
     */
    val reloadTasks: HashSet<()->Unit> = HashSet()

    /**
     * 执行加载任务
     */
    operator fun invoke()
    {
        ForumLogger.config("Loading configs...")
        reloadTasks.forEach {
            ForumLogger.severe("在重载配置文件时出现错误, 无法执行重载: $it", it)
        }
    }

    /**
     * 从配置文件中获取配置, 需要T是可序列化的.在读取失败时抛出错误
     * @param filename 配置文件名
     * @return 配置
     */
    inline fun <reified T> getConfig(filename: String): T = Yaml.decodeFromString(getConfigFile(filename).readText())

    /**
     * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时抛出错误
     * @param filename 配置文件名
     * @param path 配置路径
     * @return 配置
     */
    inline fun <reified T> getConfig(filename: String, path: String): T
    {
        val m = Yaml.decodeYamlMapFromString(getConfigFile(filename).readText())
        var e: YamlElement = m
        path.split(".").forEach {
            val map = e as? YamlMap ?: throw IllegalArgumentException("path $path not found")
            e = map[it] ?: throw IllegalArgumentException("path $path not found")
        }
        return Yaml.decodeFromString(e.toString())
    }

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
     * @param path 配置路径
     * @param default 默认值
     * @return 配置
     */
    inline fun <reified T> getConfig(filename: String, path: String, default: T): T =
        runCatching { getConfig<T>(filename, path) }.getOrDefault(default)

    /**
     * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回null
     * @param filename 配置文件名
     * @return 配置
     */
    inline fun <reified T> getConfigOrNull(filename: String): T? = getConfig(filename, null)

    /**
     * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回null
     * @param filename 配置文件名
     * @param path 配置路径
     * @return 配置
     */
    inline fun <reified T> getConfigOrNull(filename: String, path: String): T? = getConfig(filename, path, null)

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
    fun getResource(path: String): InputStream?
    {
        if (path.startsWith("/")) return Loader::class.java.getResource(path)?.openStream()
        return Loader::class.java.getResource("/$path")?.openStream()
    }
}